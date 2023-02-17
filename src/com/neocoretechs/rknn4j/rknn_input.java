package com.neocoretechs.rknn4j;
/**
 * The input information for rknn_input_set.
 * @author Jonathan Groff Copyright (c) NeoCoreTechs 2023
 *
 */

import com.neocoretechs.rknn4j.RKNN.rknn_tensor_format;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_type;

public class rknn_input {
	  int index;                                     /* the input index. */
	  long buf;                                          /* the input buf for index. */
	  int size;                                      /* the size of input buf. */
	  byte pass_through;                             /* 
	  													pass through mode.
	                                                   	if TRUE, the buf data is passed directly to the input node of the rknn model
	                                                    without any conversion. the following variables do not need to be set.
	                                                    if FALSE, the buf data is converted into an input consistent with the model
	                                                    according to the following type and fmt. so the following variables
	                                                    need to be set.
	                                                   */
	  rknn_tensor_type type;                          /* the data type of input buf. */
	  rknn_tensor_format fmt;                         /* the data format of input buf.
	                                                     currently the internal input format of NPU is NCHW by default.
	                                                  */
	/**
	 * @return the index
	 */
	public int getIndex() {
		return index;
	}
	/**
	 * @param index the index to set
	 */
	public void setIndex(int index) {
		this.index = index;
	}
	/**
	 * @return the buf
	 */
	public long getBuf() {
		return buf;
	}
	/**
	 * @param buf the buf to set
	 */
	public void setBuf(long buf) {
		this.buf = buf;
	}
	/**
	 * @return the size
	 */
	public int getSize() {
		return size;
	}
	/**
	 * @param size the size to set
	 */
	public void setSize(int size) {
		this.size = size;
	}
	/**
	 * @return the pass_through
	 */
	public byte getPass_through() {
		return pass_through;
	}
	/**
	 * @param pass_through the pass_through to set
	 */
	public void setPass_through(byte pass_through) {
		this.pass_through = pass_through;
	}
	/**
	 * @return the type
	 */
	public rknn_tensor_type getType() {
		return type;
	}
	/**
	 * @param type the type to set
	 */
	public void setType(rknn_tensor_type type) {
		this.type = type;
	}
	/**
	 * @return the fmt
	 */
	public rknn_tensor_format getFmt() {
		return fmt;
	}
	/**
	 * @param fmt the fmt to set
	 */
	public void setFmt(rknn_tensor_format fmt) {
		this.fmt = fmt;
	}                                          
}
