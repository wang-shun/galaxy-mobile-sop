package com.galaxyinternet.common.annotation;

/**
 * 
 *
 * @Description: 数据库存储的每条记录的类型：用于区分是创意还是项目
 *
 */
public enum RecordType {

	PROJECT((byte) 0, "项目"), IDEAS((byte) 1, "创意");

	private byte type;
	private String name;

	private RecordType(byte type, String name) {
		this.type = type;
		this.name = name;
	}

	public byte getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public static String getName(byte type) {
		RecordType[] rtypes = RecordType.values();
		String result = "";
		for (RecordType rtype : rtypes) {
			if (type == rtype.getType()) {
				result = rtype.getName();
				break;
			}
		}
		return result;
	}
}