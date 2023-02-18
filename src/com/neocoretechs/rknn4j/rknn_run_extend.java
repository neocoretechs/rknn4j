package com.neocoretechs.rknn4j;
/**
 * The extend information for rknn_run.
 * @author Jonathan Groff copyright (C0 NeoCreTEchs 2023
 *
 */
public class rknn_run_extend {
	   long frame_id;                                  /* output parameter, indicate current frame id of run. */
	   int non_block;                                  /* block flag of run, 0 is block else 1 is non block */
	   int timeout_ms;                                 /* timeout for block mode, in milliseconds */
	   int fence_fd;   
}
