package com.neocoretechs.rknn4j;
/**
 * 
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class rknn_devices_id {
    String[] types = new String[RKNN.RKNN_MAX_DEVS];        /* the array of device type. */
    String[] ids = new String[RKNN.RKNN_MAX_DEVS];          /* the array of device ID. */
	/**
	 * @return the types
	 */
	public String[] getTypes() {
		return types;
	}
	/**
	 * @param types the types to set
	 */
	public void setTypes(String[] types) {
		this.types = types;
	}
	/**
	 * @return the ids
	 */
	public String[] getIds() {
		return ids;
	}
	/**
	 * @param ids the ids to set
	 */
	public void setIds(String[] ids) {
		this.ids = ids;
	}
}
