package com.neocoretechs.rknn4j.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.neocoretechs.rknn4j.RKNN;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_format;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_type;
import com.neocoretechs.rknn4j.rknn_input;
import com.neocoretechs.rknn4j.rknn_input_output_num;
import com.neocoretechs.rknn4j.rknn_sdk_version;
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
	/**
	 * Set the input vector
	 * @return
	 */
	public void setInputs(int width, int height, int channels, byte[] buf) {
		rknn_input[] inputs = new rknn_input[1];
		inputs[0] = new rknn_input();
		inputs[0].setIndex(0);
		inputs[0].setType(rknn_tensor_type.RKNN_TENSOR_UINT8);
		inputs[0].setSize( width * height * channels);
		inputs[0].setFmt(rknn_tensor_format.RKNN_TENSOR_NHWC);
		int res = npu.rknn_inputs_set(0,inputs);
		if(res != RKNN.RKNN_SUCC)
			throw new RuntimeException(RKNN.get_error_string(res));
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
		Instance image = Instance.readFile(args[1]);
		m.setInputs(image.getWidth(),image.getHeight(),image.getChannels(),image.getRGB888());
		m.destroy();
	}
}
