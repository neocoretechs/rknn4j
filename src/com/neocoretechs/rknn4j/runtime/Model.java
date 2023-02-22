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
import com.neocoretechs.rknn4j.rknn_sdk_version;
import com.neocoretechs.rknn4j.rknn_tensor_attr;
import com.neocoretechs.rknn4j.rknpu2;
import com.neocoretechs.rknn4j.image.Instance;

public class Model {
	private rknpu2 npu = new rknpu2();
	
	public byte[] load(String file) throws IOException  {
		return Files.readAllBytes(Paths.get(file));
	}
	
	/**
	 * Initialize the given model. this is always step 1.
	 * @param model
	 */
	public void init(byte[] model) {
		int res = npu.rknn_init(model, model.length, RKNN.RKNN_FLAG_PRIOR_HIGH);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
	}
	
	/**
	 * Query the SDK version used to interface to the NPU
	 * @return
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
	 * @return
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
	
	public rknn_tensor_attr queryInputAttrs(int index) {
		rknn_tensor_attr inputAttrs = new rknn_tensor_attr();
		inputAttrs.setIndex(index);
		int res = npu.rknn_query_input_attr(inputAttrs);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
		return inputAttrs;
	}
	
	/**
	 * Set the input vector
	 * @return
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
		m.init(model);
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
		m.setInputs(widthHeightChannel[0],widthHeightChannel[1],widthHeightChannel[2],inputAttrs[0].getType(),inputAttrs[0].getFmt(),image.getRGB888());
		System.out.println("Preparing to run...");
		m.npu.rknn_run(null);
		m.destroy();
	}
}
