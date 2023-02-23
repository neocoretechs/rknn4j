package com.neocoretechs.rknn4j.runtime;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

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
	private rknpu2 npu = new rknpu2();
	/**
	 * Load a file and return byte array, presumably to load model from storage.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public byte[] load(String file) throws IOException  {
		return Files.readAllBytes(Paths.get(file));
	}
	
	/**
	 * Initialize the given model. this is always step 1.
	 * Performs rknn_init
	 * @param model The model loaded from desired source in rknn format.
	 * @throws RuntimeException if query fails
	 */
	public void init(byte[] model) {
		int res = npu.rknn_init(model, model.length, RKNN.RKNN_FLAG_PRIOR_HIGH);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * Query the SDK version used to interface to the NPU.
	 * Performs rknn_query_sdk.
	 * @return rknn_sdk_version from query
	 * @throws RuntimeException if query fails.
	 */
	public rknn_sdk_version querySDK() {
		rknn_sdk_version sdk = new rknn_sdk_version();
		int res = npu.rknn_query_sdk(sdk);
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
	public rknn_input_output_num queryIONumber() {
		rknn_input_output_num ioNum = new rknn_input_output_num();
		int res = npu.rknn_query_IO_num(ioNum);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return ioNum;
	}
	
	public rknn_tensor_attr queryOutputAttrs(int index) {
		rknn_tensor_attr outputAttrs = new rknn_tensor_attr();
		outputAttrs.setIndex(index);
		int res = npu.rknn_query_output_attr(outputAttrs);
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
	public rknn_tensor_attr queryInputAttrs(int index) {
		rknn_tensor_attr inputAttrs = new rknn_tensor_attr();
		inputAttrs.setIndex(index);
		int res = npu.rknn_query_input_attr(inputAttrs);
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
	public void setInputs(int width, int height, int channels, rknn_tensor_type type, rknn_tensor_format fmt, byte[] buf) {
		rknn_input[] inputs = new rknn_input[1];
		inputs[0] = new rknn_input();
		inputs[0].setIndex(0);
		inputs[0].setType(type);
		inputs[0].setSize( width * height * channels);
		inputs[0].setFmt(fmt);
		inputs[0].setBuf(buf);
		inputs[0].setPass_through(false);
		int res = npu.rknn_inputs_set(1,inputs);
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
	public void getOutputs(int nOutputs, rknn_output[] outputs) {
		int res = npu.rknn_outputs_get(nOutputs, outputs, null);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * Perform actual inference using initialized model, data, and parameters
	 * @throws RuntimeException if run fails
	 */
	public void run() {
		int res = npu.rknn_run(null);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	/**
	 * n_dims will be 4 for one input with a 4 element array, and 5 for multiple outputs with 4 element arrays
	 * the arrays are 1,h,w,channels  or 1,channel,h,w depending on format NCHW or NHWC
	 * @param inputAttr
	 * @return
	 */
	public static int[] getWidthHeightChannel(rknn_tensor_attr inputAttr) {
		int[] whc = new int[3]; 
		switch(inputAttr.getFmt()) {
			case RKNN_TENSOR_NCHW:
				whc[0] = inputAttr.getDim(3);
				whc[1] = inputAttr.getDim(2);
				whc[2] = inputAttr.getDim(1);
				return whc;
			case RKNN_TENSOR_FORMAT_MAX:
			case RKNN_TENSOR_NC1HWC2:
				break;
			case RKNN_TENSOR_NHWC:
				whc[0] = inputAttr.getDim(2);
				whc[1] = inputAttr.getDim(1);
				whc[2] = inputAttr.getDim(3);
				return whc;
			case RKNN_TENSOR_UNDEFINED:
			default:
				break;
		}
		throw new RuntimeException("Unsupported model format");
	}

	public void destroy() {
		int res = npu.rknn_destroy();
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * java com.neocoretechs.rknn4j.runtime.Model <model_file> <image jpeg file>
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Model m = new Model();
		byte[] model = m.load(args[0]);
		long tim = System.currentTimeMillis();
		m.init(model);
		System.out.println("Init time:"+(System.currentTimeMillis()-tim)+" ms.");
		rknn_sdk_version sdk = m.querySDK();
		rknn_input_output_num ioNum = m.queryIONumber();
		System.out.printf("%s %s%n", sdk, ioNum);
		BufferedImage bimage = Instance.readBufferedImage(args[1]);
		Instance image = null;
		rknn_tensor_attr[] inputAttrs = new rknn_tensor_attr[ioNum.getN_input()];
		for(int i = 0; i < ioNum.getN_input(); i++) {
			inputAttrs[i] = m.queryInputAttrs(i);
			System.out.println("Tensor input layer "+i+" attributes:");
			System.out.println(RKNN.dump_tensor_attr(inputAttrs[i]));
		}
		int[] widthHeightChannel = getWidthHeightChannel(inputAttrs[0]);
		int[] dimsImage = Instance.computeDimensions(bimage);
		if(widthHeightChannel[0] != dimsImage[0] || widthHeightChannel[1] != dimsImage[1]) {
			System.out.printf("Resizing image from %s to %s%n",Arrays.toString(dimsImage),Arrays.toString(widthHeightChannel));
			image = new Instance(args[1], bimage, args[1], widthHeightChannel[0], widthHeightChannel[1]);
		} else {
			image = new Instance(args[1], bimage, args[1]);
		}
		for(int i = 0; i < ioNum.getN_output(); i++) {
			rknn_tensor_attr outputAttr = m.queryOutputAttrs(i);
			System.out.println("Tensor output layer "+i+" attributes:");
			System.out.println(RKNN.dump_tensor_attr(outputAttr));
		}
		System.out.println("Setting inputs...");
		tim = System.currentTimeMillis();
		m.setInputs(widthHeightChannel[0],widthHeightChannel[1],widthHeightChannel[2],inputAttrs[0].getType(),inputAttrs[0].getFmt(),image.getRGB888());
		System.out.println("Set inputs time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Setting up outputs..");
		// no preallocation of output image buffers, no force floating output
		tim = System.currentTimeMillis();
		rknn_output[] outputs = m.setOutputs(ioNum.getN_output(), false, true); // last param is wantFloat, to force output to floating
		System.out.println("Set outputs time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Preparing to run...");
		tim = System.currentTimeMillis();
		m.run();
		System.out.println("Run time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Getting outputs...");
		tim = System.currentTimeMillis();
		m.getOutputs(ioNum.getN_output(), outputs);
		System.out.println("Get outputs time:"+(System.currentTimeMillis()-tim)+" ms.");
		System.out.println("Outputs:"+Arrays.toString(outputs));
		m.destroy();
	}
}
