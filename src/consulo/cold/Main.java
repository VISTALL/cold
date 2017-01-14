package consulo.cold;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.ZipUtil;
import consulo.cold.util.JavaCommandBuilder;

/**
 * @author VISTALL
 * @since 12.04.2016
 */
public class Main
{
	private static final String ourDefaultPluginHost = "https://hub.consulo.io/api/repository/";

	private static final String INTERNAL = "consulo.internal.jenkins.helper";

	private static final String[] requiredPluginList = new String[]{
			"consulo.devkit",
			"consulo.java",
			"consulo.jflex",
			"org.intellij.groovy",
			"org.intellij.intelliLang",
			"com.intellij.junit",
			"com.intellij.properties",
			"com.intellij.regexp",
			"com.intellij.spellchecker",
			"com.intellij.uiDesigner",
			"com.intellij.xml",
			// internal plugin
			INTERNAL
	};

	private static final String ourConsuloBootBuild = "1550";

	public static void main(String[] args) throws Exception
	{
		Logger.setFactory(ColdLoggerFactory.class);

		File tempDirectory = new File(".", ".cold");

		FileUtilRt.delete(tempDirectory);

		FileUtilRt.createDirectory(tempDirectory);

		File consuloBuildFile = FileUtilRt.createTempFile("consulo", "tar.gz");

		FileOutputStream fileOutputStream = new FileOutputStream(consuloBuildFile);

		System.out.println("Downloading consulo build");

		URL url = new URL(buildUrl("nightly", "consulo-win-no-jre"));

		FileUtilRt.copy(url.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Extracting consulo build");

		extract(consuloBuildFile, tempDirectory);

		FileUtilRt.delete(consuloBuildFile);

		String javaHome = System.getProperty("java.home");

		if(javaHome == null)
		{
			System.out.println("No java home");
			System.exit(-1);
			return;
		}

		if(javaHome.endsWith("jre"))
		{
			javaHome = new File(javaHome).getParent();
		}

		if(javaHome == null)
		{
			System.out.println("No jdk home");
			System.exit(-1);
			return;
		}

		File consuloPath = new File(tempDirectory, "Consulo");

		downloadColdRunner(consuloPath);

		for(String pluginId : requiredPluginList)
		{
			downloadRequiredPlugin(consuloPath, pluginId);
		}

		int exitValue = start(javaHome, consuloPath.getPath(), tempDirectory.getParentFile().getAbsolutePath());

		///FileUtilRt.delete(tempDirectory);

		System.exit(exitValue);
	}

	public static void extract(File tarFile, File directory) throws IOException
	{
		try (TarArchiveInputStream in = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarFile))))
		{
			ArchiveEntry entry = in.getNextEntry();
			while(entry != null)
			{
				if(entry.isDirectory())
				{
					entry = in.getNextEntry();
					continue;
				}
				File curfile = new File(directory, entry.getName());
				File parent = curfile.getParentFile();
				if(!parent.exists())
				{
					parent.mkdirs();
				}
				OutputStream out = new FileOutputStream(curfile);
				IOUtils.copy(in, out);
				out.close();
				entry = in.getNextEntry();
			}
		}
	}

	private static void downloadColdRunner(File consuloPath) throws Exception
	{
		URL coldJar = new URL("https://github.com/consulo/cold/raw/master/build/cold-runner.jar");

		String file = System.getProperty("cold.runner.jar");
		if(file != null)
		{
			coldJar = new File(file).toURI().toURL();
		}

		File coldJarFile = new File(consuloPath, "platform/build" + ourConsuloBootBuild + "/lib/cold-runner.jar");

		FileOutputStream fileOutputStream = new FileOutputStream(coldJarFile);

		System.out.println("Downloading cold-runner.jar");

		FileUtilRt.copy(coldJar.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Downloaded cold-runner.jar");
	}

	private static void downloadRequiredPlugin(File consuloPath, String pluginId) throws Exception
	{
		String channel = "nightly";
		if(pluginId.equals(INTERNAL))
		{
			channel = "internal";
		}

		String downloadUrl = buildUrl(channel, pluginId);

		URL url = new URL(downloadUrl);

		File tempFile = File.createTempFile(pluginId, "zip");

		FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

		System.out.println("Downloading required plugin: " + pluginId + ", url: " + url);

		FileUtilRt.copy(url.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Extracting required plugin: " + pluginId);

		ZipUtil.extract(tempFile, new File(consuloPath, "platform/build" + ourConsuloBootBuild + "/plugins"), null);

		FileUtilRt.delete(tempFile);
	}

	private static String buildUrl(String channel, String pluginId)
	{
		return ourDefaultPluginHost + "download?channel=" + channel + "&platformVersion=" + ourConsuloBootBuild + "&pluginId=" + pluginId + "&platformBuildSelect=true&noTracking=true";
	}

	private static int start(String javaHome, String consuloPath, String workingDirectory) throws Exception
	{
		JavaCommandBuilder javaCommandBuilder = new JavaCommandBuilder();
		//javaCommandBuilder.addVmArgument("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
		javaCommandBuilder.setMainClassName("consulo.cold.runner.Main");
		javaCommandBuilder.setJavaHome(javaHome);

		File home = new File(consuloPath, "platform/build" + ourConsuloBootBuild);
		File libDir = new File(home, "lib");
		for(File file : libDir.listFiles())
		{
			javaCommandBuilder.addClassPathEntry(file.getAbsolutePath());
		}

		// copy all cold properties
		Set<String> properties = System.getProperties().stringPropertyNames();
		for(String property : properties)
		{
			if(property.startsWith("cold."))
			{
				javaCommandBuilder.addSystemProperty(property, System.getProperty(property));
			}
		}

		javaCommandBuilder.addSystemProperty("jdk6.home", javaHome);
		javaCommandBuilder.addSystemProperty("consulo.home", consuloPath);

		return execute(javaCommandBuilder.construct(), workingDirectory);
	}

	private static int execute(String[] args, String workDir) throws Exception
	{
		final Process process;

		System.out.println("Executing command: " + StringUtil.join(args, " "));

		process = new ProcessBuilder(args).directory(new File(workDir)).redirectErrorStream(true).start();

		try
		{
			Thread thread = new Thread()
			{
				@Override
				public void run()
				{
					InputStream inputStream = process.getInputStream();

					try
					{
						int b;
						while((b = inputStream.read()) != -1)
						{
							String s = String.valueOf((char) b);
							System.out.print(s);
						}
					}
					catch(IOException e)
					{
						e.printStackTrace();
					}
				}
			};
			thread.setDaemon(true);
			thread.start();

			return process.waitFor();
		}
		catch(InterruptedException e)
		{
			process.destroy();
		}
		return -1;
	}
}
