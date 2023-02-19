package com.neocoretechs.rknn4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class rknpu2 {
	 private enum LibraryState {
		    NOT_LOADED,
		    LOADING,
		    LOADED
	 }
	 private static final AtomicReference<LibraryState> libraryLoaded = new AtomicReference<>(LibraryState.NOT_LOADED);
	 
	 static {
		    rknpu2.loadLibrary(Arrays.asList(new File(System.getProperty("java.library.path")).list()));
	 }

	 /**
	  * Tries to load the necessary library files from the given list of
	  * directories.
	  *
	  * @param paths a list of strings where each describes a directory of a library.
	  */
	 public static void loadLibrary(final List<String> paths) {
		    if (libraryLoaded.get() == LibraryState.LOADED) {
		      return;
		    }
		    if (libraryLoaded.compareAndSet(LibraryState.NOT_LOADED,LibraryState.LOADING)) {
		      boolean success = false;
		      UnsatisfiedLinkError err = null;
		      System.out.println("Loading from paths list of length:"+paths.size());
		      for (final String path : paths) {
		        try {
		          System.out.println(path);
		          if(path.endsWith(".so") || path.endsWith(".dll")) {
		        	  System.out.println("Trying load for:"+path);
		        	  System.load(new File(path).getAbsolutePath());
		        	  success = true;
		          }
		        } catch (final UnsatisfiedLinkError e) {
		          err = e;
		          success = false;
		          break;
		        }
		      }
		      if (!success) {
		        libraryLoaded.set(LibraryState.NOT_LOADED);
		        throw err;
		      }
		      libraryLoaded.set(LibraryState.LOADED);
		      return;
		    }

		    while (libraryLoaded.get() == LibraryState.LOADING) {
		      try {
		    	System.out.println("Waiting for load, retry..");
		        Thread.sleep(10);
		      } catch(final InterruptedException e) {
		        //ignore
		      }
		    }
	 }

	/**
	 * rknn_init
     * initial the context and load the rknn model. We will keep context internal
     * @param model rknn model.
     * @param size the size of rknn model.
     * @param flag extend flag, see the define of RKNN_FLAG_XXX_XXX.
     * @return int error code.
	 */
	public native int rknn_init(byte[] model, int size, int flag);

	/**
	* rknn_init2
    * initial the context and load the rknn model (version 2).
    * @param model the rknn model.
    * @param size the size of rknn model.
    * @param flag extend flag, see the define of RKNN_FLAG_XXX_XXX.
    * @param rknn_init_extend extend the extend information of init.
    * @return int error code.
	 */
	public native int rknn_init2(byte[] model, int size, int flag, rknn_init_extend extend);

	/**
	 * rknn_destroy
     * unload the rknn model and destroy the context.
     * @return int error code.
	 */
	public native int rknn_destroy();

	/**  
	* rknn_query sdk version
    * query the information about model or others. see rknn_query_cmd.
    * @param sdk version buffer instance to set
    * @return int error code.
	*/
	public native int rknn_query_sdk(rknn_sdk_version info);
	
	/**  
	* rknn_query input and output nodes.
    * query the information about model or others. see rknn_query_cmd.
    * After we query the input and output numbers, we can initialize the arrays to
    * query each input and output layer via getN_input and getN_output from the info instance.
    * @param the input_output_num instance to set
    * @return int error code.
	*/
	public native int rknn_query_IO_num(rknn_input_output_num info);
	
	/**  
	* rknn_query input layer.
    * query the information about model or others. see rknn_query_cmd.
    * After we query the input and output numbers, we can initialize the arrays to
    * query each input and output layer via getN_input and getN_output from the info instance.
    * rknn_tensor_attr[] inputs = new rknn_tensor_attr[rknn_input_output_num.getN_input()]; <br/>
    * for(int i = 0; i < rknn_input_output_num.getN_input()]; i++) { <br/>
    *   inputs[i].index = i;     <br/>
    *   int ret = rknn_query_input_attr(inputs[i]);     <br/>
    *   if(ret < 0)      <br/>
    *   	throw new Error();    <br/>
    * }     <br/>
    * @param the input_output_num instance to set
    * @return int error code.
	*/
	public native int rknn_query_input_attr(rknn_tensor_attr info);
	
	/**  
	* rknn_query output layer.
    * query the information about model or others. see rknn_query_cmd.
    * After we query the input and output numbers, we can initialize the arrays to
    * query each input and output layer via getN_input and getN_output from the info instance.
    * rknn_tensor_attr[] outputs = new rknn_tensor_attr[rknn_input_output_num.getN_ouput()]; <br/>
    * for(int i = 0; i < rknn_input_output_num.getN_output()]; i++) { <br/>
    *   outputs[i].index = i;     <br/>
    *   int ret = rknn_query_output_attr(outputs[i]);     <br/>
    *   if(ret < 0)      <br/>
    *   	throw new Error();    <br/>
    * }     <br/>
    * @param the output_output_num instance to set
    * @return int error code.
	*/
	public native int rknn_query_output_attr(rknn_tensor_attr info);
	
	/**
	* rknn_inputs_set
    * set inputs information by input index of rknn model.
    * inputs information see rknn_input.
    * @param n_inputs   the number of inputs.
    * @param rknn_input inputs[]         the arrays of inputs information, see rknn_input.
    * @return int error code
	 */
	public native int rknn_inputs_set(int n_inputs, rknn_input inputs[]);

	/**
	*   rknn_run
    * run the model to execute inference.
    * this function does not block normally, but it blocks when more than 3 inferences
    * are not obtained by rknn_outputs_get.
    * @param rknn_run_extend extend     the extend information of run.
    * @return int                         error code.
	*/
	public native int rknn_run(rknn_run_extend extend);

	/**
	* rknn_outputs_get
    * wait the inference to finish and get the outputs.
    * this function will block until inference finish.
    * the results will set to outputs[].
    * @param n_outputs the number of outputs.
    * @param rknn_output outputs[] the arrays of output, see rknn_output.
    * @param rknn_output_extend the extend information of output.
    * @return int error code.
	*/
	public native int rknn_outputs_get(int n_outputs, rknn_output outputs[], rknn_output_extend extend);

	/** 
	* rknn_outputs_release
    * release the outputs that get by rknn_outputs_get.
    * after called, the rknn_output[x].buf get from rknn_outputs_get will
    * also be free when rknn_output[x].is_prealloc = FALSE.
    * @param n_ouputs the number of outputs.
    * @param rknn_output outputs[] the arrays of output.
    * @return: int error code
	 */
	public native int rknn_outputs_release(int n_ouputs, rknn_output outputs[]);

}
