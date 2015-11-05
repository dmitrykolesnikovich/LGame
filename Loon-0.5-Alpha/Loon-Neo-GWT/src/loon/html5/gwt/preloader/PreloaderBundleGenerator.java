/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * @project loon
 * @author cping
 * @email：javachenpeng@yahoo.com
 * @version 0.5
 */
package loon.html5.gwt.preloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import loon.LSystem;
import loon.html5.gwt.preloader.AssetFilter.AssetType;
import loon.utils.Base64Coder;

import com.google.gwt.core.ext.BadPropertyValueException;
import com.google.gwt.core.ext.ConfigurationProperty;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

public class PreloaderBundleGenerator extends Generator {

	static class Var {
		String name;
		String context;
	}

	@Override
	public String generate(TreeLogger logger, GeneratorContext context,
			String typeName) throws UnableToCompleteException {
		System.out.println("location : " + new File(".").getAbsolutePath());
		String assetPath = getAssetPath(context);
		System.out.println("set assets path : " + assetPath);
		String assetOutputPath = getAssetOutputPath(context);
		if (assetOutputPath == null) {
			assetOutputPath = "war/";
		}
		AssetFilter assetFilter = getAssetFilter(context);
		ResourcesWrapper source = new ResourcesWrapper(assetPath);
		String[] source_list = source.file().list();
		int idx = 0;
		boolean fix_loc = false;
		// 防止定位到奇怪的assets资源目录……
		if (source_list != null && source_list.length > 0) {
			for (String file : source_list) {
				if (file.contains("assets/loon_logo.png")
						|| file.contains("assets/loon_pad_ui.png")
						|| file.contains("assets/loon_ui.png")
						|| file.contains("assets.txt")) {
					idx++;
				}
				if (idx >= 1) {
					fix_loc = true;
					break;
				}
			}
		}
		if (fix_loc) {
			source = new ResourcesWrapper("../" + assetPath);
		}
		if (!source.exists() || source.list().length == 0) {
			source = new ResourcesWrapper("../" + assetPath);
			if (!source.exists() || source.list().length == 0) {
				source = new ResourcesWrapper(getPath(assetPath).substring(
						assetPath.indexOf('/') + 1, assetPath.length()));
				if (!source.exists() || source.list().length == 0) {
					source = new ResourcesWrapper(getPath(assetPath).replace(
							assetOutputPath, "").replace("../", ""));
					if (!source.exists()) {
						throw new RuntimeException(
								"assets path '"
										+ assetPath
										+ "' does not exist. Check your loon.assetpath property in your GWT project's module gwt.xml file");
					}
				}
			}
		}
		if (!source.isDirectory()) {
			throw new RuntimeException(
					"assets path '"
							+ assetPath
							+ "' is not a directory. Check your loon.assetpath property in your GWT project's module gwt.xml file");
		}
		System.out.println("Copying resources from " + assetPath + " to "
				+ assetOutputPath);
		System.out.println(source.file.getAbsolutePath());
		ResourcesWrapper target = new ResourcesWrapper("assets/");
		System.out.println(target.file.getAbsolutePath());
		if (!target.file.getAbsolutePath().replace("\\", "/")
				.endsWith(assetOutputPath + "assets")) {
			target = new ResourcesWrapper(assetOutputPath + "assets/");
		}
		if (target.exists()) {
			if (!target.deleteDirectory()) {
				throw new RuntimeException("Couldn't clean target path '"
						+ target + "'");
			}
		}
		ArrayList<Asset> assets = new ArrayList<Asset>();
		boolean addtojs = false;
		ConfigurationProperty property = null;
		try {
			property = context.getPropertyOracle().getConfigurationProperty(
					"loon.addtojs");
			if (property != null && property.getValues().size() > 0) {
				String parameter = property.getValues().get(0).toLowerCase();
				if ("yes".equals(parameter) || "true".equals(parameter)
						|| "ok".equals(parameter)) {
					addtojs = true;
				}
			}
		} catch (BadPropertyValueException e) {
			addtojs = false;
		}
		if (addtojs) {
			// 附带一提，这里不能调用jsni给LocalAssetResources赋值，因为动态加载的关系，gwt不会处理此文件中内容，所以用@方式引用不了gwt中对象，只能传基本对象和一维数组给gwt识别.
			// 要想在这个js直接引用gwt中对象，需要一个执行顺序更优先的外部程序执行自己的编译顺序才行，而不能让gwt自行处理，但暂时还没有打算。
			StringBuilder dcode = new StringBuilder();
			dcode.append("function LocalResources(){\n");
			// 先传个LocalAssetResources过来备用，如果自制开发工具的话，此处可以直接做注入
			// PS:还有个流氓招数，就是分别穷举函数名，记录混淆前和混淆后的实际函数名以及参数，然后动态传值，不过那样太流氓，不考虑……
			dcode.append("this.running = function(res){\n");
			dcode.append("var list = new Array();");
			ArrayList<String> list = listFile(source.file());
			for (String fileName : list) {
				ResourcesWrapper fileRes = new ResourcesWrapper(fileName);
				System.out.println("<<" + fileRes.path() + ">>");
				String extension = fileRes.extension().toLowerCase();
				if (LSystem.isText(extension)) {
					String path = getPath(fileRes.path());
					String resName = getResName(path);
					Var var = getVarText(resName, path);
					if (var != null) {
						dcode.append('\n');
						dcode.append(var.context);
						dcode.append('\n');
						push(dcode, resName, var.name, true);
					}
				} else if (LSystem.isImage(extension)) {
					String path = getPath(fileRes.path());
					String resName = getResName(path);
					dcode.append('\n');
					if (addtojs) {
						String result;
						try {
							result = new String(Base64Coder.encode(fileRes
									.readBytes()));
						} catch (IOException e) {
							result = "";
						}
						StringBuilder ctx = new StringBuilder();
						char[] chars = result.toCharArray();
						int size = chars.length;
						int count = 0;
						for (int i = 0; i < size; i++) {
							char ch = chars[i];
							if (count == 156) {
								ctx.append("\"+\n");
								count = 0;
								ctx.append("\"");
							}
							ctx.append(ch);
							count++;

						}
						push(dcode, resName, ctx.toString(), false);
					} else {
						push(dcode, resName, fileRes.length(), true);
					}
				} else if (LSystem.isAudio(extension)) {
					copyDirectory(source, target, assetFilter, assets);
				} else {
					String path = getPath(fileRes.path());
					String resName = getResName(path);
					dcode.append('\n');
					if (addtojs) {
						String result;
						try {
							result = new String(Base64Coder.encode(fileRes
									.readBytes()));
						} catch (IOException e) {
							result = "";
						}
						StringBuilder ctx = new StringBuilder();
						char[] chars = result.toCharArray();
						int size = chars.length;
						int count = 0;
						for (int i = 0; i < size; i++) {
							char ch = chars[i];
							if (count == 128) {
								ctx.append("\"+\n");
								count = 0;
								ctx.append("\"");
							}
							ctx.append(ch);
							count++;
						}
						push(dcode, resName, ctx.toString(), false);
					}
				}
			}
			dcode.append("return list;};};");

			ResourcesWrapper res = new ResourcesWrapper(target.path()
					+ "/resources.js");
			res.writeString(dcode.toString(), false, LSystem.ENCODING);

			return createDummyClass(logger, context);
		}

		copyDirectory(source, target, assetFilter, assets);

		List<String> classpathFiles = getClasspathFiles(context);
		for (String classpathFile : classpathFiles) {
			System.out.println(classpathFile);
			if (assetFilter.accept(classpathFile, false)) {
				try {
					InputStream is = context.getClass().getClassLoader()
							.getResourceAsStream(classpathFile);
					ResourcesWrapper dest = target.child(classpathFile);
					dest.write(is, false);
					assets.add(new Asset(dest, assetFilter.getType(dest.path())));
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		HashMap<String, ArrayList<Asset>> bundles = new HashMap<String, ArrayList<Asset>>();
		for (Asset asset : assets) {
			String bundleName = assetFilter.getBundleName(asset.file.path());
			if (bundleName == null) {
				bundleName = "assets";
			}
			ArrayList<Asset> bundleAssets = bundles.get(bundleName);
			if (bundleAssets == null) {
				bundleAssets = new ArrayList<Asset>();
				bundles.put(bundleName, bundleAssets);
			}
			bundleAssets.add(asset);
		}

		for (Entry<String, ArrayList<Asset>> bundle : bundles.entrySet()) {
			StringBuffer buffer = new StringBuffer();
			for (Asset asset : bundle.getValue()) {
				String path = asset.file.path().replace('\\', '/')
						.replace(assetOutputPath, "")
						.replaceFirst("assets/", "");
				if (path.startsWith("/"))
					path = path.substring(1);
				buffer.append(asset.type.code);
				buffer.append(":");
				buffer.append(path);
				buffer.append(":");
				buffer.append(asset.file.isDirectory() ? 0 : asset.file
						.length());
				buffer.append(":");
				String mimetype = URLConnection
						.guessContentTypeFromName(asset.file.name());
				String ext = LSystem.getExtension(asset.file.name())
						.toLowerCase();
				if (ext.equals("an") || ext.equals("tmx")) {
					buffer.append("text/plain");
				} else {
					buffer.append(mimetype == null ? "application/unknown"
							: mimetype);
				}
				buffer.append("\n");
			}
			target.child(bundle.getKey() + ".txt").writeString(
					buffer.toString(), false);
		}
		return createDummyClass(logger, context);
	}

	private static ArrayList<String> listFile(File file) {
		ArrayList<String> ret = new ArrayList<String>();
		String[] listFile = file.list();
		if (listFile != null) {
			for (int i = 0; i < listFile.length; i++) {
				File tempfile = new File(file.getPath() + '/' + listFile[i]);
				if (tempfile.isDirectory()) {
					ArrayList<String> ls = listFile(tempfile);
					ret.addAll(ls);
					ls.clear();
					ls = null;
				} else {
					ret.add(tempfile.getPath());
				}
			}
		}
		return ret;
	}

	private class Asset {
		ResourcesWrapper file;
		AssetType type;

		public Asset(ResourcesWrapper file, AssetType type) {
			this.file = file;
			this.type = type;
		}
	}

	private static void push(StringBuilder code, String key, Object value,
			boolean allplus) {
		if (allplus) {
			code.append(String.format("list.push({k:%s,v:%s});", "\"" + key
					+ "\"", value));
		} else {
			if (value instanceof String) {
				String result = (String) value;
				if (result.startsWith("\"") || result.startsWith("\\\"")) {
					code.append(String.format("list.push({k:%s,v:%s});", "\""
							+ key + "\"", result));
				} else {
					code.append(String.format("list.push({k:%s,v:%s});", "\""
							+ key + "\"", "\"" + result + "\""));
				}
			} else {
				code.append(String.format("list.push({k:%s,v:%s});", "\"" + key
						+ "\"", value));
			}
		}
	}

	private void copyFile(ResourcesWrapper source, ResourcesWrapper dest,
			AssetFilter filter, ArrayList<Asset> assets) {
		if (!filter.accept(dest.path(), false))
			return;
		try {
			assets.add(new Asset(dest, filter.getType(dest.path())));
			dest.write(source.read(), false);
		} catch (Exception ex) {
			throw new RuntimeException("Error copying source file: " + source
					+ "\n" //
					+ "To destination: " + dest, ex);
		}
	}

	private void copyDirectory(ResourcesWrapper sourceDir,
			ResourcesWrapper destDir, AssetFilter filter,
			ArrayList<Asset> assets) {
		if (!filter.accept(destDir.path(), true))
			return;
		assets.add(new Asset(destDir, AssetType.Directory));
		destDir.mkdirs();
		ResourcesWrapper[] files = sourceDir.list();
		for (int i = 0, n = files.length; i < n; i++) {
			ResourcesWrapper srcFile = files[i];
			ResourcesWrapper destFile = destDir.child(srcFile.name());
			if (srcFile.isDirectory())
				copyDirectory(srcFile, destFile, filter, assets);
			else
				copyFile(srcFile, destFile, filter, assets);
		}
	}

	private AssetFilter getAssetFilter(GeneratorContext context) {
		ConfigurationProperty assetFilterClassProperty = null;
		try {
			assetFilterClassProperty = context.getPropertyOracle()
					.getConfigurationProperty("loon.assetfilterclass");
		} catch (BadPropertyValueException e) {
			return new DefaultAssetFilter();
		}
		if (assetFilterClassProperty.getValues().size() == 0) {
			return new DefaultAssetFilter();
		}
		String assetFilterClass = assetFilterClassProperty.getValues().get(0);
		if (assetFilterClass == null)
			return new DefaultAssetFilter();
		try {
			return (AssetFilter) Class.forName(assetFilterClass).newInstance();
		} catch (Exception e) {
			throw new RuntimeException(
					"Couldn't instantiate custom AssetFilter '"
							+ assetFilterClass
							+ "', make sure the class is public and has a public default constructor",
					e);
		}
	}

	private String getAssetPath(GeneratorContext context) {
		ConfigurationProperty assetPathProperty = null;
		try {
			assetPathProperty = context.getPropertyOracle()
					.getConfigurationProperty("loon.assetpath");
		} catch (BadPropertyValueException e) {
			throw new RuntimeException(
					"No loon.assetpath defined. Add <set-configuration-property name=\"loon.assetpath\" value=\"relative/path/to/assets/\"/> to your GWT projects gwt.xml file");
		}
		if (assetPathProperty.getValues().size() == 0) {
			throw new RuntimeException(
					"No loon.assetpath defined. Add <set-configuration-property name=\"loon.assetpath\" value=\"relative/path/to/assets/\"/> to your GWT projects gwt.xml file");
		}
		String paths = assetPathProperty.getValues().get(0);
		if (paths == null) {
			throw new RuntimeException(
					"No loon.assetpath defined. Add <set-configuration-property name=\"loon.assetpath\" value=\"relative/path/to/assets/\"/> to your GWT projects gwt.xml file");
		} else {
			String[] tokens = paths.split(",");
			for (String token : tokens) {
				if (new ResourcesWrapper(token).exists()
						|| new ResourcesWrapper("../" + token).exists()
						|| new ResourcesWrapper(getPath(token).replace("../",
								"")).exists()) {
					return token;
				}
			}
			throw new RuntimeException(
					"No valid loon.assetpath defined. Fix <set-configuration-property name=\"loon.assetpath\" value=\"relative/path/to/assets/\"/> in your GWT projects gwt.xml file");
		}
	}

	private String getAssetOutputPath(GeneratorContext context) {
		ConfigurationProperty assetPathProperty = null;
		try {
			assetPathProperty = context.getPropertyOracle()
					.getConfigurationProperty("loon.assetoutputpath");
		} catch (BadPropertyValueException e) {
			return null;
		}
		if (assetPathProperty.getValues().size() == 0) {
			return null;
		}
		String paths = assetPathProperty.getValues().get(0);
		if (paths == null) {
			return null;
		} else {
			String[] tokens = paths.split(",");
			String path = null;
			for (String token : tokens) {
				if (new ResourcesWrapper(token).exists()
						|| new ResourcesWrapper(token).mkdirs()) {
					path = token;
				}
			}
			if (path != null && !path.endsWith("/")) {
				path += "/";
			}
			return path;
		}
	}

	private List<String> getClasspathFiles(GeneratorContext context) {
		List<String> classpathFiles = new ArrayList<String>();
		try {
			ConfigurationProperty prop = context.getPropertyOracle()
					.getConfigurationProperty("loon.files.classpath");
			for (String value : prop.getValues()) {
				classpathFiles.add(value);
			}
		} catch (BadPropertyValueException e) {
		}
		return classpathFiles;
	}

	private String createDummyClass(TreeLogger logger, GeneratorContext context) {
		String packageName = "loon.html5.gwt.preloader";
		String className = "PreloaderBundleImpl";
		ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory(
				packageName, className);
		composer.addImplementedInterface(packageName + ".PreloaderBundle");
		PrintWriter printWriter = context.tryCreate(logger, packageName,
				className);
		if (printWriter == null) {
			return packageName + "." + className;
		}
		SourceWriter sourceWriter = composer.createSourceWriter(context,
				printWriter);
		sourceWriter.commit(logger);
		return packageName + "." + className;
	}

	private static String readCodeString(File file) throws Exception {
		FileInputStream in = new FileInputStream(file);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				LSystem.ENCODING));
		try {
			String text = null;
			StringBuffer dcode = new StringBuffer();
			for (; (text = reader.readLine()) != null;) {
				text = text.trim();
				if (text.length() > 0) {
					char[] chars = text.toCharArray();
					dcode.append("\"");
					int size = chars.length;
					for (int i = 0; i < size; i++) {
						char ch = chars[i];
						if (ch == '\\') {
							dcode.append('\\');
							dcode.append(ch);
						} else if (ch == '"') {
							dcode.append('\\');
							dcode.append(ch);
						} else {
							dcode.append(ch);
						}
					}
					if (size > 0
							&& chars[size - 1] != DefaultAssetFilter.special_symbols) {
						dcode.append(DefaultAssetFilter.special_symbols);
					}
					dcode.append("\"+");
					dcode.append('\n');
				}
			}
			text = dcode.toString();
			int idx = text.lastIndexOf('+');
			return text.substring(0, idx);
		} finally {
			reader.close();
		}
	}

	private static String getPath(String path) {
		int pathLen;
		do {
			pathLen = path.length();
			path = path.replaceAll("[^/]+/\\.\\./", "");
		} while (path.length() != pathLen);
		return path.replace("\\", "/");
	}

	private static String getResName(String fileName) {
		int idx = fileName.indexOf("assets/");
		String path = fileName;
		if (idx != -1) {
			path = fileName.substring(idx + 7, fileName.length());
		} else if ((idx = path.indexOf('/')) != -1) {
			path = fileName.substring(idx, fileName.length());
		} else {
			path = LSystem.getFileName(fileName);
		}
		return path;
	}

	private static Var getVarText(String resName, String fileName) {
		try {
			String varname = "txt_"
					+ resName.replace('.', '_').replace('/', '_');
			String result = readCodeString(new File(fileName));
			Var var = new Var();
			var.name = varname;
			var.context = ("var " + varname + " = (" + result) + ").replace('"
					+ DefaultAssetFilter.special_symbols + "', '\\n');";
			return var;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
