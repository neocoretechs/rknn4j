package com.neocoretechs.rknn4j;
/**
 * This seems to not apply to current version of RK3588
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
	/**
	 * @return the types
	 */
	public String getType(int i) {
		return types[i];
	}
	/**
	 * @param types the types to set
	 */
	public void setType(String types, int i) {
		this.types[i] = types;
	}
	/**
	 * @return the ids
	 */
	public String getId(int i) {
		return ids[i];
	}
	/**
	 * @param ids the ids to set
	 */
	public void setId(String ids, int i) {
		this.ids[i] = ids;
	}
	
	public String toString() {
		StringBuilder s = new StringBuilder("Devices:\r\n");
		for(int i = 0; i < RKNN.RKNN_MAX_DEVS; i++) {
			if(types[i] != null) {
				s.append(i);
				s.append(" - ");
				s.append("Type:");
				s.append(types[i]);
				s.append(" Id:");
				s.append(ids[i]);
				s.append("\r\n");
			}
		}
		return s.toString();
	}
}
