package com.neocoretechs.rknn4j;

public class RKNN {
	/* RKNN API Version */
	public static final String API_VERSION = "1.3.0";
	/*
	    Definition of extended flag for rknn_init.
	*/
	/* set high priority context. */
	public static final int RKNN_FLAG_PRIOR_HIGH = 0x00000000;

	/* set medium priority context */
	public static final int RKNN_FLAG_PRIOR_MEDIUM = 0x00000001;

	/* set low priority context. */
	public static final int RKNN_FLAG_PRIOR_LOW = 0x00000002;

	/* asynchronous mode.
	   when enable, rknn_outputs_get will not block for too long because it directly retrieves the result of
	   the previous frame which can increase the frame rate on single-threaded mode, but at the cost of
	   rknn_outputs_get not retrieves the result of the current frame.
	   in multi-threaded mode you do not need to turn this mode on. */
	public static final int RKNN_FLAG_ASYNC_MASK = 0x00000004;

	/* collect performance mode.
	   when enable, you can get detailed performance reports via rknn_query(ctx, RKNN_QUERY_PERF_DETAIL, ...),
	   but it will reduce the frame rate. */
	public static final int RKNN_FLAG_COLLECT_PERF_MASK = 0x00000008;

	/* You can store the rknn model under NPU, 
	 * when you call rknn_init(), you can pass the filename of model instead of model data.
	 * Then you can hide your model and be invisible to the end user.
	 * */
	public static final int RKNN_FLAG_LOAD_MODEL_IN_NPU = 0x00000010;

	/*
	    Error code returned by the RKNN API.
	*/
	public static final int RKNN_SUCC =                              0;       /* execute succeed. */
	public static final int RKNN_ERR_FAIL   =                        -1;      /* execute failed. */
	public static final int RKNN_ERR_TIMEOUT  =                      -2;      /* execute timeout. */
	public static final int RKNN_ERR_DEVICE_UNAVAILABLE   =          -3;      /* device is unavailable. */
	public static final int RKNN_ERR_MALLOC_FAIL     =               -4;      /* memory malloc fail. */
	public static final int RKNN_ERR_PARAM_INVALID    =              -5;      /* parameter is invalid. */
	public static final int RKNN_ERR_MODEL_INVALID    =              -6;      /* model is invalid. */
	public static final int RKNN_ERR_CTX_INVALID      =              -7;      /* context is invalid. */
	public static final int RKNN_ERR_INPUT_INVALID    =              -8;      /* input is invalid. */
	public static final int RKNN_ERR_OUTPUT_INVALID     =            -9;      /* output is invalid. */
	public static final int RKNN_ERR_DEVICE_UNMATCH     =            -10;     /* the device is unmatch, please update rknn sdk
	                                                           and npu driver/firmware. */
	public static final int RKNN_ERR_INCOMPATILE_PRE_COMPILE_MODEL = -11;     /* This RKNN model use pre_compile mode, but not compatible with current driver. */
	/*
	    Definition for tensor
	*/
	public static final int RKNN_MAX_DIMS        =                   16;      /* maximum dimension of tensor. */
	public static final int RKNN_MAX_NAME_LEN     =                  256;     /* maximum name lenth of tensor. */

	/*
	    Definition for deivce id
	*/
	public static final int RKNN_MAX_DEVS         =                  256;     /* maximum number of device. */
	public static final int RKNN_MAX_DEV_LEN      =                  64;      /* maximum id/type lenth of device. */

	long rknn_context;

	/*
	    The query command for rknn_query
	*/
	public enum rknn_query_cmd {
	    RKNN_QUERY_IN_OUT_NUM,                          /* query the number of input & output tensor. */
	    RKNN_QUERY_INPUT_ATTR,                              /* query the attribute of input tensor. */
	    RKNN_QUERY_OUTPUT_ATTR,                             /* query the attribute of output tensor. */
	    RKNN_QUERY_PERF_DETAIL,                             /* query the detail performance, need set
	                                                           RKNN_FLAG_COLLECT_PERF_MASK when call rknn_init,
	                                                           this query needs to be valid after rknn_outputs_get. */
	    RKNN_QUERY_PERF_RUN,                                /* query the time of run,
	                                                           this query needs to be valid after rknn_outputs_get. */
	    RKNN_QUERY_SDK_VERSION,                             /* query the sdk & driver version */

	    RKNN_QUERY_CMD_MAX
	};

	/*
	    the tensor data type.
	*/
	public enum rknn_tensor_type {
		RKNN_TENSOR_FLOAT32 ,                            /* data type is float32. */
		RKNN_TENSOR_FLOAT16,                                /* data type is float16. */
		RKNN_TENSOR_INT8,                                   /* data type is int8. */
		RKNN_TENSOR_UINT8,                                  /* data type is uint8. */
		RKNN_TENSOR_INT16,                                  /* data type is int16. */
		RKNN_TENSOR_UINT16,                                 /* data type is uint16. */
		RKNN_TENSOR_INT32,                                  /* data type is int32. */
		RKNN_TENSOR_UINT32,                                 /* data type is uint32. */
		RKNN_TENSOR_INT64,                                  /* data type is int64. */
		RKNN_TENSOR_BOOL,
		RKNN_TENSOR_TYPE_MAX
	};

	/*
	    the quantitative type.
	*/
	public enum rknn_tensor_qnt_type {
	    RKNN_TENSOR_QNT_NONE,                           /* none. */
	    RKNN_TENSOR_QNT_DFP,                                /* dynamic fixed point. */
	    RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC,                  /* asymmetric affine. */
	    RKNN_TENSOR_QNT_MAX
	};

	/*
	    the tensor data format.
	*/
	public enum rknn_tensor_format {
		RKNN_TENSOR_NCHW ,                               /* data format is NCHW. */
		RKNN_TENSOR_NHWC,                                   /* data format is NHWC. */
		RKNN_TENSOR_NC1HWC2,                                /* data format is NC1HWC2. */
		RKNN_TENSOR_UNDEFINED,
		RKNN_TENSOR_FORMAT_MAX
	};
	
	public static String dump_tensor_attr(rknn_tensor_attr attr) {
	  return String.format("  index=%d, name=%s, n_dims=%d, dims=[%d, %d, %d, %d], n_elems=%d, size=%d, fmt=%s, type=%s, qnt_type=%s, zp=%d, scale=%f\n",
	         attr.index, attr.name, attr.n_dims, attr.dims[0], attr.dims[1], attr.dims[2], attr.dims[3],
	         attr.n_elems, attr.size, get_format_string(attr.fmt), get_type_string(attr.type), get_qnt_type_string(attr.qnt_type), attr.zp, attr.scale);
	}
	
	static String get_type_string(rknn_tensor_type type)
	{
	    switch(type) {
	    	case RKNN_TENSOR_FLOAT32: return "FP32";
	    	case RKNN_TENSOR_FLOAT16: return "FP16";
	    	case RKNN_TENSOR_INT8: return "INT8";
	    	case RKNN_TENSOR_UINT8: return "UINT8";
	    	case RKNN_TENSOR_INT16: return "INT16";
	    	case RKNN_TENSOR_UINT16: return "UINT16";
	    	case RKNN_TENSOR_INT32: return "INT32";
	    	case RKNN_TENSOR_UINT32: return "UINT32";
	    	case RKNN_TENSOR_INT64: return "INT64";
	    	case RKNN_TENSOR_BOOL: return "BOOL";
	    	default: return "UNKNOW";
	    }
	}
	
	static String get_qnt_type_string(rknn_tensor_qnt_type type)
	{
	    switch(type) {
	    	case RKNN_TENSOR_QNT_NONE: return "NONE";
	    	case RKNN_TENSOR_QNT_DFP: return "DFP";
	    	case RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC: return "AFFINE";
	    	default: return "UNKNOW";
	    }
	}

	static String get_format_string(rknn_tensor_format fmt)
	{
	    switch(fmt) {
	    	case RKNN_TENSOR_NCHW: return "NCHW";
	    	case RKNN_TENSOR_NHWC: return "NHWC";
	    	case RKNN_TENSOR_NC1HWC2: return "NC1HWC2";
	    	case RKNN_TENSOR_UNDEFINED: return "UNDEFINED";
	    	default: return "UNKNOW";
	    }
	}
}
