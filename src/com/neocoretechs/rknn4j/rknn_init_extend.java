package com.neocoretechs.rknn4j;

public class rknn_init_extend {
    long ctx;                               /* rknn context */
    int     real_model_offset;                      /* real rknn model file size, only valid when init context with rknn file path */
    int     real_model_size;                        /* real rknn model file offset, only valid when init context with rknn file path */
    byte[]  reserved = new byte[120];     
}
