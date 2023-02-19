package com.neocoretechs.rknn4j;
/**
 * The information for RKNN_QUERY_IN_OUT_NUM.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 */
public class rknn_input_output_num {
    private int n_input;                                   /* the number of input. */
    private int n_output;                                  /* the number of output. */
	/**
	 * @return the n_input
	 */
	public int getN_input() {
		return n_input;
	}
	/**
	 * @param n_input the n_input to set
	 */
	public void setN_input(int n_input) {
		this.n_input = n_input;
	}
	/**
	 * @return the n_output
	 */
	public int getN_output() {
		return n_output;
	}
	/**
	 * @param n_output the n_output to set
	 */
	public void setN_output(int n_output) {
		this.n_output = n_output;
	}
	
	@Override
	public String toString() {
		return String.format("Number inputs=%d, Number outputs=%d", n_input, n_output);
	}
}
