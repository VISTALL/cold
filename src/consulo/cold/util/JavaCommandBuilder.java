/*
 * Vulcan Build Manager
 * Copyright (C) 2005-2012 Chris Eldredge
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package consulo.cold.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

public class JavaCommandBuilder
{
	private static final Logger logger = Logger.getInstance(JavaCommandBuilder.class);

	private static final String JAVA_EXECUTABLE_PATH = "bin" + File.separator + "java";
	private static final String EXECUTABLE_EXTENSION = ".exe";

	private String javaExecutablePath;
	private String mainClassName;

	private Map<String, String> systemProperties = new HashMap<String, String>();
	// we need reverse order, move util up to consulo-platform-impl, due classloading issue, duplicate CompressionUtil
	private Set<String> classPath = new TreeSet<>(Comparator.<String>reverseOrder());
	private Set<String> vmArguments = new TreeSet<>();
	private List<String> arguments = new ArrayList<String>();

	public String[] construct()
	{
		final List<String> args = new ArrayList<String>();

		args.add(javaExecutablePath);

		args.addAll(vmArguments);

		if(!classPath.isEmpty())
		{
			args.add("-classpath");
			args.add(StringUtil.join(classPath, File.pathSeparator));
		}

		for(String property : systemProperties.keySet())
		{
			final StringBuilder arg = new StringBuilder("-D");
			arg.append(property);
			arg.append("=");
			arg.append(systemProperties.get(property));

			args.add(arg.toString());
		}

		args.add(mainClassName);

		for(String arg : arguments)
		{
			args.add(arg);
		}

		return args.toArray(new String[args.size()]);
	}

	public void addClassPathEntry(String path)
	{
		classPath.add(path);
	}

	public void addSystemProperty(String key, String value)
	{
		if(systemProperties.containsKey(key))
		{
			logger.debug("Overriding system property " + key);
		}
		systemProperties.put(key, value);
	}

	public void addArgument(String arg)
	{
		arguments.add(arg);
	}

	public void addVmArgument(String arg)
	{
		vmArguments.add(arg);
	}

	public String getJavaExecutablePath()
	{
		return javaExecutablePath;
	}

	public void setJavaExecutablePath(String javaExecutablePath)
	{
		this.javaExecutablePath = javaExecutablePath;
	}

	public void setJavaHome(String javaHome)
	{
		try
		{
			final File javaProgram = getVirtualMachineExecutable(javaHome);
			setJavaExecutablePath(javaProgram.getCanonicalPath());
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static File getVirtualMachineExecutable(String javaHome)
	{
		File file = new File(javaHome, JAVA_EXECUTABLE_PATH);

		if(!file.exists())
		{
			file = new File(javaHome, JAVA_EXECUTABLE_PATH + EXECUTABLE_EXTENSION);

			if(!file.exists())
			{
				throw new RuntimeException(javaHome + " is not valid as java home");
			}
		}
		return file;
	}

	public String getMainClassName()
	{
		return mainClassName;
	}

	public void setMainClassName(String mainClassName)
	{
		this.mainClassName = mainClassName;
	}

	public Set<String> getClassPath()
	{
		return Collections.unmodifiableSet(classPath);
	}

	public Set<String> getVmArguments()
	{
		return vmArguments;
	}
}