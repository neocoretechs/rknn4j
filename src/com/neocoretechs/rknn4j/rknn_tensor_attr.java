package com.neocoretechs.rknn4j;

import com.neocoretechs.rknn4j.RKNN.rknn_tensor_format;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_qnt_type;
import com.neocoretechs.rknn4j.RKNN.rknn_tensor_type;

/**
 * The information for RKNN_QUERY_INPUT_ATTR / RKNN_QUERY_OUTPUT_ATTR.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class rknn_tensor_attr {
	   int index;                                     /* input parameter, the index of input/output tensor,need set before call rknn_query. */
	   int n_dims;                                    /* the number of dimensions. */
	   int[] dims = new int[RKNN.RKNN_MAX_DIMS];      /* the dimensions array. */
	   String name;                       			  /* the name of tensor. */
	   int n_elems;                                   /* the number of elements. */
	   int size;                                      /* the bytes size of tensor. */
	   rknn_tensor_format fmt;                        /* the data format of tensor. */
	   rknn_tensor_type type;                         /* the data type of tensor. */
	   rknn_tensor_qnt_type qnt_type;                 /* the quantitative type of tensor. */
	   byte fl;                                       /* fractional length for RKNN_TENSOR_QNT_DFP. */
	   int zp;                                        /* zero point for RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC. */
	   float scale;                                   /* scale for RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC. */
	   int w_stride;                                  /* the stride of tensor along the width dimension of input, Note: it is read-only, 0 means equal to width. */
	   int size_with_stride;                          /* the bytes size of tensor with stride. */
	   byte pass_through;                             /* pass through mode, for rknn_set_io_mem interface. if TRUE, the buf data is passed directly to the input node of the rknn model
                 										without any conversion. the following variables do not need to be set.
        												if FALSE, the buf data is converted into an input consistent with the model according to the following type and fmt. 
        												so the following variables need to be set.*/
	   int h_stride;                 
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
	 * @return the n_dims
	 */
	public int getN_dims() {
		return n_dims;
	}
	/**
	 * @param n_dims the n_dims to set
	 */
	public void setN_dims(int n_dims) {
		this.n_dims = n_dims;
	}
	/**
	 * @return the dims
	 */
	public int[] getDims() {
		return dims;
	}
	public int getDim(int i) {
		return dims[i];
	}
	/**
	 * @param dims the dims to set
	 */
	public void setDims(int[] dims) {
		this.dims = dims;
	}
	public void setDim(int i, int dim) {
		this.dims[i] = dim;
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the n_elems
	 */
	public int getN_elems() {
		return n_elems;
	}
	/**
	 * @param n_elems the n_elems to set
	 */
	public void setN_elems(int n_elems) {
		this.n_elems = n_elems;
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
	 * @return the qnt_type
	 */
	public rknn_tensor_qnt_type getQnt_type() {
		return qnt_type;
	}
	/**
	 * @param qnt_type the qnt_type to set
	 */
	public void setQnt_type(rknn_tensor_qnt_type qnt_type) {
		this.qnt_type = qnt_type;
	}
	/**
	 * @return the fl
	 */
	public byte getFl() {
		return fl;
	}
	/**
	 * @param fl the fl to set
	 */
	public void setFl(byte fl) {
		this.fl = fl;
	}
	/**
	 * @return the zp
	 */
	public int getZp() {
		return zp;
	}
	/**
	 * @param zp the zp to set
	 */
	public void setZp(int zp) {
		this.zp = zp;
	}
	/**
	 * @return the scale
	 */
	public float getScale() {
		return scale;
	}
	/**
	 * @param scale the scale to set
	 */
	public void setScale(float scale) {
		this.scale = scale;
	}
	/**
	 * @return the w_stride
	 */
	public int getW_stride() {
		return w_stride;
	}
	/**
	 * @param w_stride the w_stride to set
	 */
	public void setW_stride(int w_stride) {
		this.w_stride = w_stride;
	}
	/**
	 * @return the size_with_stride
	 */
	public int getSize_with_stride() {
		return size_with_stride;
	}
	/**
	 * @param size_with_stride the size_with_stride to set
	 */
	public void setSize_with_stride(int size_with_stride) {
		this.size_with_stride = size_with_stride;
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
	 * @return the h_stride
	 */
	public int getH_stride() {
		return h_stride;
	}
	/**
	 * @param h_stride the h_stride to set
	 */
	public void setH_stride(int h_stride) {
		this.h_stride = h_stride;
	}
	
	/**
	 * n_dims will be 4 for one input with a 4 element array, and 5 for multiple outputs with 4 element arrays
	 * the arrays are 1,h,w,channels  or 1,channel,h,w depending on format NCHW or NHWC
	 * @param inputAttr
	 * @return the array of width/height/channel
	 */
	public int[] getWidthHeightChannel() {
		int[] whc = new int[3]; 
		switch(getFmt()) {
			case RKNN_TENSOR_NCHW:
				whc[0] = getDim(3);
				whc[1] = getDim(2);
				whc[2] = getDim(1);
				return whc;
			case RKNN_TENSOR_FORMAT_MAX:
			case RKNN_TENSOR_NC1HWC2:
				break;
			case RKNN_TENSOR_NHWC:
				whc[0] = getDim(2);
				whc[1] = getDim(1);
				whc[2] = getDim(3);
				return whc;
			case RKNN_TENSOR_UNDEFINED:
			default:
				break;
		}
		throw new RuntimeException("Unsupported model format");
	}
	
}
