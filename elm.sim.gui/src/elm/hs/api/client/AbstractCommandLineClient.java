package elm.hs.api.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import elm.hs.api.HomeServerService;

public abstract class AbstractCommandLineClient {

	protected static final String OPT_PASSWD = "p";
	protected static final String OPT_PUBLIC_URI = "publicUri";
	protected static final String OPT_INTERNAL_URI = "internalUri";
	protected static final String OPT_DEVICE = "d";
	protected static final String OPT_NO_INTERNAL = "noint";
	protected static final String OPT_VERBOSE = "v";

	protected final Logger LOG = Logger.getLogger(getClass().getName());

	protected String user = HomeServerService.ADMIN_USER;
	protected String password = HomeServerService.DEFAULT_PASSWORD;
	protected URI publicBaseUri = HomeServerService.DEFAULT_URI;
	protected URI internalBaseUri = HomeServerService.INTERNAL_API_URI;
	protected String deviceID;
	protected boolean useInternalClient;
	protected boolean verbose;

	/**
	 * Command-line options, see <a href="http://commons.apache.org/proper/commons-cli/usage.html">Apache Commons CLI</a>.
	 * 
	 * @param options
	 *            cannot be {@code null}
	 */
	protected void addCommandLineOptions(Options options) {
		assert options != null;
		options.addOption(OPT_PASSWD, "password", true, null);
		options.addOption(OPT_PUBLIC_URI, "public_api_server_base_uri", true, "the server's base URI for the public API");
		options.addOption(OPT_INTERNAL_URI, "internal_api_server_base_uri", true, "the server's base URI for the public API");
		options.addOption(OPT_DEVICE, "device", true, "the device ID");
		options.addOption(OPT_NO_INTERNAL, "no_internal", false, "do not use the internal server API");
		options.addOption(OPT_VERBOSE, "verbose", false, null);
	}

	/**
	 * @param line
	 *            cannot be {@code null}
	 * @throws ParseException 
	 */
	protected void processOptions(CommandLine line) throws ParseException {
		assert line != null;
		if (line.hasOption(OPT_PASSWD)) {
			password = line.getOptionValue(OPT_PASSWD);
		}
		if (line.hasOption(OPT_PUBLIC_URI)) {
			final String uri = line.getOptionValue(OPT_PUBLIC_URI);
			try {
				publicBaseUri = new URI(uri);
			} catch (URISyntaxException e) {
				throw new ParseException("Illegal " + OPT_PUBLIC_URI + ": " + uri);
			}
		}
		if (line.hasOption(OPT_INTERNAL_URI)) {
			final String uri = line.getOptionValue(OPT_INTERNAL_URI);
			try {
				internalBaseUri = new URI(uri);
			} catch (URISyntaxException e) {
				throw new ParseException("Illegal " + OPT_INTERNAL_URI + ": " + uri);
			}
		}
		if (line.hasOption(OPT_DEVICE)) {
			deviceID = line.getOptionValue(OPT_DEVICE);
		}
		useInternalClient = !line.hasOption(OPT_NO_INTERNAL);
		verbose = line.hasOption(OPT_VERBOSE);
	}

	/**
	 * Command-line options, see <a href="http://commons.apache.org/proper/commons-cli/usage.html">Apache Commons CLI</a>.
	 * 
	 * @param args
	 *            cannot be {@code null}
	 */
	protected void parseCommandLine(String[] args) {
		assert args != null;
		Options options = new Options();
		addCommandLineOptions(options);
		try {
			CommandLine line = new BasicParser().parse(options, args);
			processOptions(line);
		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(getCommandLineSyntax(options), options);
			System.exit(1);
		}
	}

	/**
	 * Returns a human-readable command-line syntax description. This should really be part of the used Apache CLI framework.
	 * 
	 * @param options
	 *            cannot be {@code null}
	 * @return never {@code null}
	 */
	protected String getCommandLineSyntax(Options options) {
		StringBuilder b = new StringBuilder();
		@SuppressWarnings("unchecked")
		List<Option> list = new ArrayList<Option>(options.getOptions());
		// sort required first, then sort alphabetically:
		Collections.sort(list, new Comparator<Option>() {

			@Override
			public int compare(Option o1, Option o2) {
				if (o1.isRequired() == o2.isRequired()) {
					return o1.getOpt().compareTo(o2.getOpt());
				}
				if (o1.isRequired()) {
					return -1;
				}
				return 1;
			}
		});
		for (Option opt : list) {
			addOption(b, opt);
		}
		return b.toString();
	}

	protected void addOption(StringBuilder b, Option opt) {
		if (!opt.isRequired()) {
			b.append("[");
		}
		b.append("-");
		b.append(opt.getOpt());
		if (opt.hasArg()) {
			b.append(" arg");
			if (opt.hasArgs()) {
				b.append("...");
			}
		}
		if (!opt.isRequired()) {
			b.append("]");
		}
		b.append(" ");
	}
}
