package com.neocoretechs.rknn4j.runtime;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import com.neocoretechs.rknn4j.RKNN;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_format;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_type;
import com.neocoretechs.rknn4j.rknn_input;
import com.neocoretechs.rknn4j.rknn_input_output_num;
import com.neocoretechs.rknn4j.rknn_output;
import com.neocoretechs.rknn4j.rknn_sdk_version;
import com.neocoretechs.rknn4j.rknn_tensor_attr;
import com.neocoretechs.rknn4j.rknpu2;
import com.neocoretechs.rknn4j.image.Instance;
import com.neocoretechs.rknn4j.image.detect_result;
import com.neocoretechs.rknn4j.image.detect_result_group;
/**
 * Java bindings to RockChip RK3588 Neural Processing Unit SDK.
 * The business model, supporting the neural network models.
 * Sets up the various structures to perform inference on the embedded SoC NPU using
 * pretrained models of your choosing.<p/>
 * Main method provides object segmentation and recognition and has been tested with the ssd_inception_v2
 * and YOLOv5s models provided with SDK.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class Model {
	private static boolean DEBUG = false;
	private rknpu2 npu = new rknpu2();
	
	private static boolean INCEPTION = false;
	private static boolean WANTFLOAT = false;

	/**
	 * Load a file and return byte array, presumably to load model from storage.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public byte[] load(String file) throws IOException  {
		return Files.readAllBytes(Paths.get(file));
	}
	public static String[] loadLines(String file) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
		String[] slines = new String[lines.size()];
		for(int i = 0; i < slines.length; i++) {
			slines[i] = lines.get(i);
		}
		return slines;
	}
	/**
	 * Load the detection box anchors for InceptionSSD model from designated file
	 * @param file                                                                                                                                                                                                                                                                                                                                                                                                                                               
	 * @param num total number of elements to expect (1917)
	 * @return The parsed float array from file with [4][num] elements
	 * @throws IOException
	 */
	public static float[][] loadBoxPriors(String file, int num) throws IOException {
		float[][] ret = new float[4][num];
		String[] lines = loadLines(file);
		for (int i = 0; i < 4; i++) {
			int cnt = 0;
			String[] sfloat = lines[i].split("\\s");
			for(String s: sfloat) {
				if(s == null || s.length() == 0) 
					continue;
				if(DEBUG )
					System.out.println("i="+i+" cnt="+cnt+" "+s);
				ret[i][cnt++] = Float.parseFloat(s);
			}
		}
		return ret;
	}

	/**
	 * Initialize the given model. this is always step 1.
	 * Performs rknn_init
	 * @param model The model loaded from desired source in rknn format.
	 * @throws RuntimeException if query fails
	 */
	public long init(byte[] model) {
		long res = npu.rknn_init(model, model.length, RKNN.RKNN_FLAG_PRIOR_HIGH);
		//if(res != RKNN.RKNN_SUCC)
		if(res >= -11 && res <=0) // return codes that are not valid context
			throw new RuntimeException(RKNN.get_error_string((int) res));
		return res;
	}
	
	/**
	 * Query the SDK version used to interface to the NPU.
	 * Performs rknn_query_sdk.
	 * @return rknn_sdk_version from query
	 * @throws RuntimeException if query fails.
	 */
	public rknn_sdk_version querySDK(long ctx) {
		rknn_sdk_version sdk = new rknn_sdk_version();
		int res = npu.rknn_query_sdk(ctx, sdk);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return sdk;
	}
	
	/**
	 * Query the number of inputs to, and outputs from the currently loaded model.
	 * Performs rknn_query_IO_num.
	 * @return The queried rknn_input_output_num
	 * @throws RuntimeException if query fails.
	 */
	public rknn_input_output_num queryIONumber(long ctx) {
		rknn_input_output_num ioNum = new rknn_input_output_num();
		int res = npu.rknn_query_IO_num(ctx, ioNum);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return ioNum;
	}
	
	public rknn_tensor_attr queryOutputAttrs(long ctx, int index) {
		rknn_tensor_attr outputAttrs = new rknn_tensor_attr();
		outputAttrs.setIndex(index);
		int res = npu.rknn_query_output_attr(ctx, outputAttrs);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return outputAttrs;
	}
	/**
	 * Query the input attributes. will perform the rknn_query_input_attr call to NPU.
	 * @param index model attribute index
	 * @return the queried rknn_tensor_attr instance from NPU from the model index
	 * @throws RuntimeException if query fails
	 */
	public rknn_tensor_attr queryInputAttrs(long ctx, int index) {
		rknn_tensor_attr inputAttrs = new rknn_tensor_attr();
		inputAttrs.setIndex(index);
		int res = npu.rknn_query_input_attr(ctx, inputAttrs);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return inputAttrs;
	}
	
	/**
	 * Set the input vector. We are calling rknn_inputs_set inside the routine
	 * such that initialization is complete. It is assumed that the image has been read, properly
	 * resized to match the model if necessary, a proper tensor type and format are provided
	 * and the buffer is loaded with image data.
	 * @param width image width
	 * @param height image height
	 * @param channels image channels, 3 for RGB, 1 for grayscale etc.
	 * @param type rknn_tensor_type from enum
	 * @param fmt rknn_tensor_fmt from enum
	 * @param buf image bytes in format to match model
	 * @throws RuntimeException i input call fails.
	 */
	public void setInputs(long ctx, int size, rknn_tensor_type type, rknn_tensor_format fmt, byte[] buf) {
		rknn_input[] inputs = new rknn_input[1];
		inputs[0] = new rknn_input();
		inputs[0].setIndex(0);
		inputs[0].setType(type);
		inputs[0].setSize(size);
		inputs[0].setFmt(fmt);
		inputs[0].setBuf(buf);
		inputs[0].setPass_through(false);
		int res = npu.rknn_inputs_set(ctx, 1, inputs);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	/**
	 * Set the outputs to prepare for extraction after rknn_run.<p/>
	 * There are two ways to store buffers for output data:
	 * 1) The user allocate and release buffers themselves. In this case, the rknn_output.is_prealloc
	 * to be set to 1, and the rknn_output.buf points to users allocated buffer;
	 * 2) The other is allocated by rknn. At this time, the rknn_output .is_prealloc needs to be set to
	 * 0. After the function is executed, rknn_output.buf will be created and store the output data.
	 * rknn_output outputs[io_num.n_output];
	 * for (int i = 0; i < io_num.n_output; i++) {
	 * 	outputs[i].index = i;
	 * 	outputs[i].is_prealloc = 0;
	 * 	outputs[i].want_float = 1;
	 * }
	 * ret = rknn_outputs_get(ctx, io_num.n_output, outputs, NULL);
	 * @param nOutput Number of output buffers from rknn_input_output_num queryIONumber getN_output()
	 * @param isPrealloc true to honor the above allocation of buffers
	 * @param wantFloat Force output to floating point
	 * @return the array of rknn_output output objects set up to call rknn_outputs_get
	 */
	public rknn_output[] setOutputs(int nOutput, boolean isPrealloc, boolean wantFloat) {
		rknn_output[] outputs = new rknn_output[nOutput];
		for(int i = 0; i < nOutput; i++) {
			outputs[i] = new rknn_output();
			outputs[i].setIndex(i);
			outputs[i].setIs_prealloc(isPrealloc);
			outputs[i].setWant_float(wantFloat);
		}
		return outputs;
	}
	/**
	 * Retrieve the outputs from the result of the inference run.
	 * Performs rknn_outputs_get
	 * @param nOutputs number outputs expected
	 * @param outputs array of outputs with index fields instantiated from setOutputs
	 * @throws RuntimeExcpetion if get fails
	 */
	public void getOutputs(long ctx, int nOutputs, rknn_output[] outputs) {
		int res = npu.rknn_outputs_get(ctx, nOutputs, outputs, null);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * Perform actual inference using initialized model, data, and parameters
	 * @throws RuntimeException if run fails
	 */
	public void run(long ctx) {
		int res = npu.rknn_run(ctx, null);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}

	/**
	 * Destroy context, passed context no longer valid. User must enforce.
	 * @param ctx
	 */
	public void destroy(long ctx) {
		int res = npu.rknn_destroy(ctx);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * java com.neocoretechs.rknn4j.runtime.Model <model_file> <image jpeg file> <inception | yolo>
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if(args.length < 3) {
			System.out.println("usage: java com.neocoretechs.rknn4j.runtime.Model <model_file> <image jpeg file> <inception | yolo>");
			System.exit(1);
		}
		Model m = new Model();
		byte[] model = m.load(args[0]);
		long tim = System.currentTimeMillis();
		long ctx = m.init(model);
		System.out.println("Init time:"+(System.currentTimeMillis()-tim)+" ms.");
		rknn_sdk_version sdk = m.querySDK(ctx);
		rknn_input_output_num ioNum = m.queryIONumber(ctx);
		System.out.printf("%s %s%n", sdk, ioNum);
		BufferedImage bimage = Instance.readBufferedImage(args[1]);
		if(args[2].equals("inception"))
			INCEPTION = true;
		Instance image = null;
		rknn_tensor_attr[] inputAttrs = new rknn_tensor_attr[ioNum.getN_input()];
		for(int i = 0; i < ioNum.getN_input(); i++) {
			inputAttrs[i] = m.queryInputAttrs(ctx, i);
			System.out.println("Tensor input layer "+i+" attributes:");
			System.out.println(RKNN.dump_tensor_attr(inputAttrs[i]));
		}
		int[] widthHeightChannel = inputAttrs[0].getWidthHeightChannel();
		int[] dimsImage = Instance.computeDimensions(bimage);
	 	float scale_w = 1.0f;//(float)widthHeightChannel[0] / (float)dimsImage[0];
	  	float scale_h = 1.0f;//(float)widthHeightChannel[1] / (float)dimsImage[1];
	  	File fi = new File(args[1]);
	  	byte[] imageInByte = Files.readAllBytes(fi.toPath());
	  	image = new Instance(args[1],dimsImage[0],dimsImage[1],widthHeightChannel[2],imageInByte,widthHeightChannel[0],widthHeightChannel[1],args[1]);

		ArrayList<rknn_tensor_attr> tensorAttrs = new ArrayList<rknn_tensor_attr>();
		for(int i = 0; i < ioNum.getN_output(); i++) {
			rknn_tensor_attr outputAttr = m.queryOutputAttrs(ctx, i);
			System.out.println("Tensor output layer "+i+" attributes:");
			System.out.println(RKNN.dump_tensor_attr(outputAttr));
			tensorAttrs.add(outputAttr);
		}
		//
		System.out.println("Setting up I/O..");

		tim = System.currentTimeMillis();
		String[] labels = null;
		if(INCEPTION) { // InceptionSSD
			//wantFloat = true; contrary to demo, we will use INT8
			labels = loadLines("/etc/model/coco_labels_list.txt");
		} else { //assume YOLOv5
			labels = loadLines("/etc/model/coco_80_labels_list.txt");
		}
		// Set input data, example of setInputs
		m.setInputs(ctx,inputAttrs[0].getSize(),inputAttrs[0].getType(),inputAttrs[0].getFmt(),image.getRGB888());
		rknn_output[] outputs = m.setOutputs(ioNum.getN_output(), false, WANTFLOAT); // last param is wantFloat, to force output to floating
		System.out.println("Total category labels="+labels.length);
		System.out.println("Setup time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Preparing to run...");
		tim = System.currentTimeMillis();
		m.run(ctx);
		System.out.println("Run time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Getting outputs...");
		tim = System.currentTimeMillis();
		m.getOutputs(ctx, ioNum.getN_output(), outputs);
		System.out.println("Get outputs time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Outputs:"+Arrays.toString(outputs));
		detect_result_group drg = new detect_result_group();
		ArrayList<Float> scales = null;
		ArrayList<Integer> zps = null;
		if(!WANTFLOAT) {
			scales = new ArrayList<Float>();
			zps = new ArrayList<Integer>();
			for(int i = 0; i < ioNum.getN_output(); i++) {
				rknn_tensor_attr outputAttr = tensorAttrs.get(i);
				zps.add(outputAttr.getZp());
				scales.add(outputAttr.getScale());
			}
		}
		if(INCEPTION) { // InceptionSSD 2 layers output
			float[][] boxPriors = loadBoxPriors("/etc/model/box_priors.txt",detect_result.NUM_RESULTS);
			if(WANTFLOAT) {
				detect_result.post_process(outputs[0].getBuf(), outputs[1].getBuf(), boxPriors,
					dimsImage[0], dimsImage[1], detect_result.NMS_THRESH_SSD, 
					scale_w, scale_h, drg, labels);
			} else {
				detect_result.post_process(outputs[0].getBuf(), outputs[1].getBuf(), boxPriors,
					dimsImage[0], dimsImage[1], detect_result.NMS_THRESH_SSD, 
					scale_w, scale_h, zps, scales, drg, labels);
			}
			System.out.println("Detected Result Group:"+drg);
			Instance.saveDetections(bimage, drg);
		} else { //YOLOv5 3 layers output
			if(WANTFLOAT) {
				detect_result.post_process(outputs,
						widthHeightChannel[1], widthHeightChannel[0], detect_result.BOX_THRESH, detect_result.NMS_THRESH, 
						scale_w, scale_h, drg, labels);			
			} else {
				detect_result.post_process(outputs,
						widthHeightChannel[1], widthHeightChannel[0], detect_result.BOX_THRESH, detect_result.NMS_THRESH, 
						scale_w, scale_h, zps, scales, drg, labels);
			}
			System.out.println("Detected Result Group:"+drg.toJson());
			image.saveDetections(drg);
		}
		m.destroy(ctx);
	}
}
