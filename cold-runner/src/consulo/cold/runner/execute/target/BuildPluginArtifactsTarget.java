package consulo.cold.runner.execute.target;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.ZipUtil;
import consulo.cold.runner.execute.ExecuteFailedException;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;

/**
 * @author VISTALL
 * @since 09-Feb-17
 */
public class BuildPluginArtifactsTarget implements ExecuteTarget
{
	@Override
	public void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		try
		{
			File workingDirectory = executeContext.getUserData(ExecuteTarget.WORKING_DIRECTORY);
			File distPath = new File(workingDirectory, "out/artifacts/dist");
			if(!distPath.exists())
			{
				executeLogger.error("'out/artifacts/dist' is not exists");
				return;
			}

			File[] filePaths = distPath.listFiles();
			if(filePaths == null)
			{
				return;
			}

			for(File pluginPath : filePaths)
			{
				// pair ID + NAME
				Map.Entry<String, String> pluginInfo = new AbstractMap.SimpleImmutableEntry<>(null, null);

				File libDir = new File(pluginPath, "lib");
				if(!libDir.exists())
				{
					throw new ExecuteFailedException("'lib' dir is not exists");
				}

				File[] libs = libDir.listFiles(pathname -> Comparing.equal("jar", FileUtilRt.getExtension(pathname.getName())));
				mainLoop:
				for(File someJar : libs)
				{
					ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(new FileInputStream(someJar));

					ArchiveEntry entry = null;
					while((entry = zipArchiveInputStream.getNextEntry()) != null)
					{
						String name = entry.getName();
						if(name.equals("META-INF/plugin.xml"))
						{
							byte[] data = IOUtils.toByteArray(zipArchiveInputStream);

							Map.Entry<String, String> temp = findPluginId(new ByteArrayInputStream(data));
							if(temp != null)
							{
								pluginInfo = temp;
							}

							break mainLoop;
						}
					}
					zipArchiveInputStream.close();
				}


				if(pluginInfo.getKey() == null && pluginInfo.getValue() == null)
				{
					throw new ExecuteFailedException("Path " + pluginPath + " is not plugin");
				}

				if(pluginInfo.getKey() == null)
				{
					throw new ExecuteFailedException("Plugin with name: " + pluginInfo.getValue() + " don't have pluginId");
				}

				if(pluginInfo.getValue() == null)
				{
					throw new ExecuteFailedException("Plugin with id: " + pluginInfo.getKey() + " don't have pluginName");
				}

				if(!pluginInfo.getKey().equals(pluginPath.getName()))
				{
					throw new ExecuteFailedException(String.format("Plugin dir(%s) is not equal pluginId(%s)", pluginPath.getName(), pluginInfo.getKey()));
				}

				File zipFile = new File(distPath, pluginInfo.getKey() + "_" + executeContext.getUserData(ExecuteTarget.BUILD_NUMBER) + ".zip");
				try(ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile)))
				{
					ZipUtil.addDirToZipRecursively(zipOutputStream, null, pluginPath, "", null, null);
				}
			}
		}
		catch(IOException e)
		{
			throw new ExecuteFailedException(e);
		}
	}

	private static Map.Entry<String, String> findPluginId(InputStream inputStream) throws IOException
	{
		String id = null;
		String name = null;

		try
		{
			SAXBuilder reader = new SAXBuilder();
			Document document = reader.build(inputStream);

			Element rootElement = document.getRootElement();
			Element temp = rootElement.getChild("id");
			if(temp != null)
			{
				id = temp.getTextTrim();
			}
			temp = rootElement.getChild("name");
			if(temp != null)
			{
				name = temp.getTextTrim();
			}
		}
		catch(JDOMException e)
		{
			throw new IOException(e);
		}

		return new AbstractMap.SimpleImmutableEntry<>(id, name);
	}
}
