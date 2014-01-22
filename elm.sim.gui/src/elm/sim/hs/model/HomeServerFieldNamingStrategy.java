package elm.sim.hs.model;

import java.lang.reflect.Field;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.FieldNamingStrategy;

/**
 * @see FieldNamingPolicy
 */
public class HomeServerFieldNamingStrategy implements FieldNamingStrategy {

	@Override
	public String translateName(Field f) {
	      return lowerCaseFirstLetter(f.getName());
	}

	/**
	 * Ensures the JSON field names begins with a lower-case letter.
	 * @see FieldNamingPolicy
	 */
	private static String lowerCaseFirstLetter(String name) {
		StringBuilder fieldNameBuilder = new StringBuilder();
		int index = 0;
		char firstCharacter = name.charAt(index);

		while (index < name.length() - 1) {
			if (Character.isLetter(firstCharacter)) {
				break;
			}

			fieldNameBuilder.append(firstCharacter);
			firstCharacter = name.charAt(++index);
		}

		if (index == name.length()) {
			return fieldNameBuilder.toString();
		}

		if (!Character.isLowerCase(firstCharacter)) {
			String modifiedTarget = modifyString(Character.toLowerCase(firstCharacter), name, ++index);
			return fieldNameBuilder.append(modifiedTarget).toString();
		} else {
			return name;
		}
	}

	/**
	 * @see FieldNamingPolicy
	 */
	private static String modifyString(char firstCharacter, String srcString, int indexOfSubstring) {
		return (indexOfSubstring < srcString.length()) ? firstCharacter + srcString.substring(indexOfSubstring) : String.valueOf(firstCharacter);
	}

}
