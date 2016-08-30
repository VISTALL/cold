package consulo.cold;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Set;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
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
	private static final String ourDefaultPluginHost = "http://must-be.org/consulo/plugins/%s";

	private static final String[] requiredPluginList = new String[]{
			"org.consulo.devkit",
			"org.intellij.groovy",
			"org.intellij.intelliLang",
			"org.consulo.java",
			"JFlex Support",
			"com.intellij.junit",
			"com.intellij.properties",
			"com.intellij.regexp",
			"com.intellij.spellchecker",
			"com.intellij.uiDesigner",
			"com.intellij.xml",
			// internal plugin
			"consulo.internal.jenkins.helper"
	};

	public static void main(String[] args) throws Exception
	{
		Logger.setFactory(ColdLoggerFactory.class);

		File tempDirectory = new File(".", ".cold");

		tempDirectory.delete();

		FileUtil.createDirectory(tempDirectory);

		File consuloBuildFile = FileUtilRt.createTempFile("consulo", "zip", true);

		FileOutputStream fileOutputStream = new FileOutputStream(consuloBuildFile);

		System.out.println("Downloading consulo build");

		URL url = new URL("http://must-be.org/vulcan/site/_consulo-distribution/out/consulo-win-no-jre.zip");

		FileUtilRt.copy(url.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Extracting consulo build");

		ZipUtil.extract(consuloBuildFile, tempDirectory, null);

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

		tempDirectory.delete();

		System.exit(exitValue);
	}

	private static void downloadColdRunner(File consuloPath) throws Exception
	{
		URL coldJar = new URL("https://github.com/consulo/cold/raw/master/build/cold-runner.jar");

		File coldJarFile = new File(consuloPath, "lib/cold-runner.jar");

		FileOutputStream fileOutputStream = new FileOutputStream(coldJarFile);

		System.out.println("Downloading cold-runner.jar");

		FileUtilRt.copy(coldJar.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Downloaded cold-runner.jar");
	}

	private static void downloadRequiredPlugin(File consuloPath, String pluginId) throws Exception
	{
		URL url = null;
		if(pluginId.equals("com.intellij.uiDesigner"))
		{
			url = new URL("https://raw.githubusercontent.com/consulo/cold/master/build/ui-designer_hacked.zip");
		}
		else
		{
			String urlString = String.format(ourDefaultPluginHost, "download?id=") + URLEncoder.encode(pluginId, "UTF8") + "&build=SNAPSHOT&uuid=" + URLEncoder.encode("cold", "UTF8");

			url = new URL(urlString);
		}

		File tempFile = FileUtilRt.createTempFile(pluginId, "zip", true);

		FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

		System.out.println("Downloading required plugin: " + pluginId + ", url: " + url);

		FileUtilRt.copy(url.openStream(), fileOutputStream);

		fileOutputStream.close();

		System.out.println("Extracting required plugin: " + pluginId);

		ZipUtil.extract(tempFile, new File(consuloPath, "plugins"), null);
	}

	private static int start(String javaHome, String consuloPath, String workingDirectory) throws Exception
	{
		JavaCommandBuilder javaCommandBuilder = new JavaCommandBuilder();
		javaCommandBuilder.setMainClassName("consulo.cold.runner.Main");
		javaCommandBuilder.setJavaHome(javaHome);

		javaCommandBuilder.addClassPathEntry(javaHome + "/lib/tools.jar");
		File libDir = new File(consuloPath, "lib");
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
