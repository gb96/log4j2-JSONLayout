/**
 * Default package
 */
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.ThreadContext.ContextStack;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttr;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

/**
 * Log4j 2.x layout for logging events as JSON.
 * 
 * Based on
 * <a href="https://github.com/Aconex/json-log4j-layout">Aconex/json-log4j-layout</a>
 * <br/>
 * which also inspired <a href="https://github.com/carrot-garden/carrot-log">carrot-garden/carrot-log</a>
 * 
 * 
 * @author bowering
 */
@Plugin(name = "JSONLayout", type = "Core", elementType = "layout", printObject = true)
public class JSONLayout extends AbstractStringLayout {

  public static final Charset UTF8 = Charset.forName("UTF-8");

	private final JsonFactory jsonFactory = new JsonFactory();

	/**
	 * Constructor
	 * 
	 * @param charset
	 */
	protected JSONLayout(final Charset charset) {
		super(charset);
	}

	/*
	 * @see
	 * org.apache.logging.log4j.core.Layout#toSerializable(org.apache.logging.
	 * log4j.core.LogEvent)
	 */
	@Override
	public String toSerializable(final LogEvent event) {
		try {
			final StringWriter stringWriter = new StringWriter();
			final JsonGenerator g = jsonFactory.createJsonGenerator(stringWriter);
			g.writeStartObject();
			writeBasicFields(event, g);
			writeMessageField(event, g);
			writeMDC(event, g);
			writeNDC(event, g);
			writeThrowableEvents(event, g);
			g.writeEndObject();
			g.close();
			stringWriter.append("\n");
			return stringWriter.toString();
		} catch (IOException e) {
			LOGGER.error("Could not write event as JSON", e);
		}
		return StringUtils.EMPTY;
	}


	private static void writeBasicFields(final LogEvent event, final JsonGenerator g)
			throws IOException {
		g.writeStringField("logger", event.getLoggerName());
		g.writeStringField("level", event.getLevel().toString());
		g.writeNumberField("timestamp", event.getMillis());
		g.writeStringField("threadName", event.getThreadName());
	}

	private static void writeMessageField(final LogEvent event,
			final JsonGenerator g) throws IOException {
		
		final Message message = event.getMessage();
		if (message == null) return;
		
		if (message instanceof MapMessage) {
			final MapMessage mapMessage = (MapMessage) message;
			final Map<String, String> map = mapMessage.getData();
			writeStringMap("msg", map, g);
		} else {
			g.writeStringField("msg", message.toString());
		}
	}

	private static void writeMDC(final LogEvent event, final JsonGenerator g) throws IOException {
		writeStringMap("mdc", event.getContextMap(), g);
	}

	private static void writeNDC(final LogEvent event, final JsonGenerator g) throws IOException {
		final ContextStack ndc = event.getContextStack();
		if (ndc == null || ndc.getDepth() == 0) return;
		
		final List<String> ndcList = ndc.asList();
		g.writeArrayFieldStart("ndc");
		for (final String stackElement : ndcList) {
			g.writeString(stackElement);
		}
		g.writeEndArray();
	}


	/**
	 * Write the given stringMap as a JSON object with the given mapName
	 *  
	 * @param mapName
	 * @param stringMap
	 * @param g
	 * @throws IOException
	 */
	private static void writeStringMap(final String mapName, final Map<String, String> stringMap, final JsonGenerator g) throws IOException {
		if (stringMap == null || stringMap.isEmpty()) return;
		g.writeFieldName(mapName);
		g.writeStartObject();
		
		// TreeSet orders fields alphabetically by key:
		final Set<String> keys = new TreeSet<String>(stringMap.keySet());
		for (final String key : keys) {
			g.writeStringField(key, stringMap.get(key));
		}
		
		g.writeEndObject();
	}

	private static void writeThrowableEvents(final LogEvent event,
			final JsonGenerator g) throws IOException {
		final Throwable thrown = event.getThrown();
		if (thrown == null)
			return;

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		thrown.printStackTrace(pw);

		String throwableString = sw.toString();
		if (throwableString.isEmpty())
			return;

		g.writeStringField("throwable", throwableString);
	}

	@PluginFactory
	public static JSONLayout createLayout(
			@PluginAttr("charset") final String charset) {
		Charset c = UTF8;
		if (charset != null) {
			if (Charset.isSupported(charset)) {
				c = Charset.forName(charset);
			} else {
				LOGGER.error("Charset " + charset
						+ " is not supported for layout, using " + c.displayName());
			}
		}
		return new JSONLayout(c);
	}

}

