package elm.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ElmLogFormatter extends Formatter {

	public static final String ELM_LOGGING_CONFIG_FILE_NAME = "elm.logging";

	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	int classNameWidth = 0; // field with for class name; this grows if longer class names are encountered

	@Override
	public String format(LogRecord record) {
		StringBuilder b = new StringBuilder(dateFormat.format(new Date(record.getMillis())));
		b.append(' ');

		if (record.getSourceClassName() != null) {
			String[] segments = record.getSourceClassName().split("\\.");
			StringBuilder className = new StringBuilder();
			for (int i = 0; i < segments.length - 1; i++) {
				className.append(segments[i].substring(0, 1));
				className.append('.');
			}
			className.append(segments[segments.length - 1]);
			b.append(className.toString());
			// pad to current max length with blanks for better readability:
			classNameWidth = Math.max(classNameWidth, className.length());
			for (int i = className.length(); i <= classNameWidth; i++) {
				b.append(' ');
			}
			b.append(' ');
		}

		b.append(record.getLevel().getName());
		b.append(' ');

		b.append(record.getMessage());

		if (record.getThrown() == null) {
			b.append("\n");
		} else {
			b.append(" -- Exception:\n");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			record.getThrown().printStackTrace(pw);
			pw.close();
			b.append(sw.toString());
		}
		return b.toString();
	}

	/**
	 * Loads the {@value #ELM_LOGGING_CONFIG_FILE_NAME} logging configuration file via the class loader.
	 * 
	 * @see #init(String)
	 */
	public static void init() throws SecurityException, IOException {
		init(ELM_LOGGING_CONFIG_FILE_NAME);
	}

	/**
	 * Loads a Java logging configuration file via the class loader and configures the {@link LogManager}.
	 * 
	 * @param classPathRelativefileName
	 *            cannot be {@code null} or empty
	 * @throws SecurityException
	 *             file could not be accessed; a message has already been logged on the default logger
	 * @throws IOException
	 *             file not found; a message has already been logged on the default logger
	 */
	public static void init(String classPathRelativefileName) throws SecurityException, IOException {
		assert classPathRelativefileName != null && !classPathRelativefileName.isEmpty();
		try {
			InputStream stream = ElmLogFormatter.class.getClassLoader().getResourceAsStream(classPathRelativefileName);
			LogManager.getLogManager().readConfiguration(stream);
		} catch (SecurityException | IOException e) {
			Logger.getLogger(ElmLogFormatter.class.getName()).log(Level.SEVERE,
					"Cannot load logging configuration file: " + e.getMessage() + " (using class loader)", e);
			throw e;
		}
	}
}