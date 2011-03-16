package com.aptana.editor.common.formatting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.aptana.core.util.IOUtil;
import com.aptana.core.util.StringUtil;
import com.aptana.formatter.IScriptFormatter;
import com.aptana.formatter.IScriptFormatterFactory;

/**
 * FormatterTestFile <br>
 * This class provides the basic functionality that should be used for all formatting test files. A formatting test file
 * is divided into different sections using tags surrounded by "==" (These tags need to be in its own separate tag). The
 * first tag should be "==PREFS==", followed by the preferences for that particular test. The second tag should be
 * "==CONTENT==", followed by the original content before it is formatted. If this is the first time the test is run,
 * the formatted content can be generated by calling the generateFormattedContent() method (You will need to make sure
 * the test file ends with a newline or the generateFormattedContent() method may not work correctly). A "==FORMATTED=="
 * tag should be added along with the formatted content.
 */

public class FormatterTestFile
{

	protected StringBuilder content = new StringBuilder();
	protected StringBuilder formattedContent = new StringBuilder();
	protected HashMap<String, String> prefs;
	protected String formatting_folder;
	protected String formatterId;
	protected IScriptFormatter formatter;
	protected String filename;

	protected static enum Tag
	{
		PREFS, CONTENT, FORMATTED, INVALID
	}

	public FormatterTestFile(IScriptFormatterFactory factory, String formatterId, String filename,
			String formattingFolder)
	{
		this.formatterId = formatterId;
		this.formatting_folder = formattingFolder;
		this.filename = formattingFolder + "/" + filename; //$NON-NLS-1$
		prefs = new HashMap<String, String>();
		try
		{
			parseFile();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		this.formatter = factory.createFormatter(System.getProperty("line.separator"), prefs); //$NON-NLS-1$
	}

	private void parseFile() throws IOException
	{

		InputStream stream = FileLocator.openStream(Platform.getBundle(formatterId), Path.fromPortableString(filename),
				false);

		String contentStr = IOUtil.read(stream);
		BufferedReader reader = new BufferedReader(new StringReader(contentStr));
		String lineRead = null;

		// We assume the first tag is the preference tag
		Tag tag = Tag.PREFS;

		while ((lineRead = reader.readLine()) != null)
		{
			if (lineRead.matches("[ ]*==[A-Z]+==")) //$NON-NLS-1$
			{
				tag = extractTag(lineRead);
			}
			else
			{
				storeLineByState(lineRead, tag);
			}
		}
		if (content.length() > 0)
		{
			content.deleteCharAt(content.length() - 1);
		}
		if (formattedContent.length() > 0 && !contentStr.endsWith("\n"))
		{
			formattedContent.deleteCharAt(formattedContent.length() - 1);
		}

		reader.close();
	}

	public String getContent()
	{
		return content.toString();
	}

	public String getFormattedContent()
	{
		return formattedContent.toString();
	}

	public IScriptFormatter getFormatter()
	{
		return formatter;
	}

	public void generateFormattedContent() throws IOException
	{
		if (formattedContent.length() > 0)
		{
			formattedContent.delete(0, formattedContent.length());
		}
		URL fileURL = FileLocator.find(Platform.getBundle(formatterId), Path.fromPortableString(filename), null);
		String file = FileLocator.toFileURL(fileURL).getFile();
		// Read all the content till we hit the word "==FORMATTED==", or till the end of the file.
		StringBuilder fileContentBuilder = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		while ((line = reader.readLine()) != null)
		{
			if ("==FORMATTED==".equals(line.trim()))
			{
				break;
			}
			fileContentBuilder.append(line);
			fileContentBuilder.append('\n');
		}
		trimTrailingWhitespaces(fileContentBuilder);
		FileWriter formattedStream = new FileWriter(file);
		TextEdit formattedTextEdit = formatter.format(content.toString(), 0, content.length(), 0, false, null,
				StringUtil.EMPTY);

		formattedStream.write(fileContentBuilder.toString());
		formattedStream.write("\n==FORMATTED==\n"); //$NON-NLS-1$

		if (formattedTextEdit instanceof ReplaceEdit)
		{
			String formatResult = ((ReplaceEdit) formattedTextEdit).getText().replaceAll("\r\n", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			formattedStream.write(formatResult);
			formattedContent.append(formatResult);
		}
		else if ((formattedTextEdit instanceof MultiTextEdit))
		{
			// write original content if the formatted text is same as original
			formattedStream.write(content.toString());
			formattedContent.append(content);
		}
		formattedStream.flush();
		formattedStream.close();
		// if formatting fails, we don't write anything
	}

	/**
	 * @param fileContentBuilder
	 */
	private void trimTrailingWhitespaces(StringBuilder builder)
	{
		for (int i = builder.length() - 1; i >= 0; i--)
		{
			if (Character.isWhitespace(builder.charAt(i)))
			{
				builder.deleteCharAt(i);
			}
			else
			{
				break;
			}
		}
	}

	protected void storeLineByState(String line, Tag tag)
	{
		switch (tag)
		{
			case CONTENT:
				content.append(line);
				content.append('\n');
				break;
			case FORMATTED:
				formattedContent.append(line);
				formattedContent.append('\n');
				break;
			case PREFS:
				// add it to the preferences hashmap
				String[] prefsBuffer = line.split("="); //$NON-NLS-1$
				if (prefsBuffer.length == 2)
				{
					prefs.put(prefsBuffer[0], prefsBuffer[1]);
				}
		}
	}

	protected Tag extractTag(String text)
	{
		text = text.replaceAll("==", ""); //$NON-NLS-1$ //$NON-NLS-2$
		text = text.replace(" ", "");
		Tag tag;
		if (text.equals(Tag.PREFS.toString()))
		{
			tag = Tag.PREFS;
		}
		else if (text.equals(Tag.CONTENT.toString()))
		{
			tag = Tag.CONTENT;
		}
		else if (text.equals(Tag.FORMATTED.toString()))
		{
			tag = Tag.FORMATTED;
		}
		else
		{
			tag = Tag.INVALID;
		}
		return tag;

	}
}
