package com.neocoretechs.rknn4j;
/**
 * The output information for rknn_outputs_get.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class rknn_output {
	   boolean want_float;                                 	/* transfer output data to float */
	   boolean is_prealloc;                                	/* whether buf is pre-allocated. if TRUE, the following variables need to be set.
	                                                       	if FALSE, the following variables do not need to be set. */
	   int index;                                     		/* the output index. */
	   byte[] buf;                                        	/* the output buf for index. when is_prealloc = FALSE and rknn_outputs_release called,
	                                                       	this buf pointer will be freed and unusable.*/
	   int size;                                      		/* the size of output buf. */
	/**
	 * @return the want_float
	 */
	public boolean getWant_float() {
		return want_float;
	}
	/**
	 * @param want_float the want_float to set
	 */
	public void setWant_float(boolean want_float) {
		this.want_float = want_float;
	}
	/**
	 * @return the is_prealloc
	 */
	public boolean getIs_prealloc() {
		return is_prealloc;
	}
	/**
	 * @param is_prealloc the is_prealloc to set
	 */
	public void setIs_prealloc(boolean is_prealloc) {
		this.is_prealloc = is_prealloc;
	}
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
	public byte[] getBuf() {
		return buf;
	}
	/**
	 * @param buf the buf to set
	 */
	public void setBuf(byte[] buf) {
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
}
