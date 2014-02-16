package elm.ui.api;



public enum ElmStatus {
	OFF, ON, SATURATION, OVERLOAD, ERROR;


	public boolean in(ElmStatus... other) {
		for (ElmStatus value : other) {
			if (this == value) return true;
		}
		return false;
	}
}
