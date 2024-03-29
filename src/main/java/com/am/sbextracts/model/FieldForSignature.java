package com.am.sbextracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FieldForSignature {
	String id;
	@JsonProperty("Page")
	Page page;
	Percentages percentages;
	boolean required = true;
	@JsonProperty("Signer")
	Signer signer;
	@JsonProperty("Group")
	Object group;
	String type = "signature";
	String fieldId;
	String subfieldId;

	@Builder
	@Value
	public static class Page {
		static Double HEIGHT = 793.76;
		static Double WIDTH = 1122.56;
		Integer num;
		Double w = HEIGHT;
		Double h = WIDTH;
		Rect rect;
	}

	@Builder
	@Value
	public static class Rect {
		Double x;
		Double y;
		Double width;
		Double height;
		Double top;
		Double right;
		Double bottom;
		Double left;
	}

	@Builder
	@Value
	public static class Percentages {
		String top;
		String left;
		String height = "3.5%";
		String width = "26%";
	}

	@Value
	public static class Signer {
		Integer color = 1;
		Integer fields = 1;
		String label = "Employee";
		Integer order = 1;
		String type = "employee";
	}
}