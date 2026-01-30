package com.tradery.dataservice.log;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;

/**
 * Log4j2 appender that writes formatted log lines to {@link InMemoryLogBuffer}.
 */
@Plugin(name = "Buffer", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class BufferAppender extends AbstractAppender {

    protected BufferAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        String formatted = getLayout() != null
            ? new String(getLayout().toByteArray(event)).stripTrailing()
            : event.getMessage().getFormattedMessage();
        InMemoryLogBuffer.getInstance().add(formatted);
    }

    @PluginFactory
    public static BufferAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout) {
        if (name == null) {
            LOGGER.error("No name provided for BufferAppender");
            return null;
        }
        return new BufferAppender(name, filter, layout);
    }
}
