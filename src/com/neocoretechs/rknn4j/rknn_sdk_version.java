package com.neocoretechs.rknn4j;
/**
 * The information for RKNN_QUERY_SDK_VERSION.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 */
public class rknn_sdk_version {
    private String api_version;                              /* the version of rknn api. */
    private String drv_version;                              /* the version of rknn driver. */
	/**
	 * @return the api_version
	 */
	public String getApi_version() {
		return api_version;
	}
	/**
	 * @param api_version the api_version to set
	 */
	public void setApi_version(String api_version) {
		this.api_version = api_version;
	}
	/**
	 * @return the drv_version
	 */
	public String getDrv_version() {
		return drv_version;
	}
	/**
	 * @param drv_version the drv_version to set
	 */
	public void setDrv_version(String drv_version) {
		this.drv_version = drv_version;
	}
	
	@Override
	public String toString() {
		return String.format("RKNN SDK - API version:%s, Driver version:%s",api_version,drv_version);
	}
}
