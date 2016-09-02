package consulo.cold.runner.execute.target;

import com.intellij.util.ArrayUtilRt;

/**
 * @author VISTALL
 * @since 02-Sep-16
 */
public class PluginJson
{
	public String id;
	public String[] dependencies = ArrayUtilRt.EMPTY_STRING_ARRAY;
}
