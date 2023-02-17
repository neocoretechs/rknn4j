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
	/**
	 * @param dims the dims to set
	 */
	public void setDims(int[] dims) {
		this.dims = dims;
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
}
