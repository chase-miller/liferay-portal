/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.gradle.plugins.patcher;

import com.liferay.gradle.util.FileUtil;
import com.liferay.gradle.util.GradleUtil;

import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.filters.FixCrLfFilter;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;
import org.gradle.util.GUtil;

/**
 * @author Andrea Di Giorgi
 */
public class PatchTask extends DefaultTask {

	public static final String PATCHED_SRC_DIR_MAPPING_DEFAULT_EXTENSION = "*";

	public PatchTask() {
		_project = getProject();
	}

	public PatchTask fileNames(Iterable<Object> fileNames) {
		GUtil.addToCollection(_fileNames, fileNames);

		return this;
	}

	public PatchTask fileNames(Object ... fileNames) {
		return fileNames(Arrays.asList(fileNames));
	}

	@Input
	public List<String> getFileNames() {
		return GradleUtil.toStringList(_fileNames);
	}

	@Input
	public String getOriginalLibConfigurationName() {
		return GradleUtil.toString(_originalLibConfigurationName);
	}

	public File getOriginalLibFile() {
		Configuration configuration = GradleUtil.getConfiguration(
			_project, getOriginalLibConfigurationName());

		ResolvedConfiguration resolvedConfiguration =
			configuration.getResolvedConfiguration();

		String moduleGroup = getOriginalLibModuleGroup();
		String moduleName = getOriginalLibModuleName();
		String moduleVersion = getOriginalLibModuleVersion();

		for (ResolvedArtifact resolvedArtifact :
				resolvedConfiguration.getResolvedArtifacts()) {

			ResolvedModuleVersion resolvedModuleVersion =
				resolvedArtifact.getModuleVersion();

			ModuleVersionIdentifier moduleVersionIdentifier =
				resolvedModuleVersion.getId();

			if (moduleGroup.equals(moduleVersionIdentifier.getGroup()) &&
				moduleName.equals(moduleVersionIdentifier.getName()) &&
				moduleVersion.equals(moduleVersionIdentifier.getVersion())) {

				return resolvedArtifact.getFile();
			}
		}

		throw new GradleException("Unable to find original lib " + moduleName);
	}

	@Input
	public String getOriginalLibModuleGroup() {
		Dependency dependency = getOriginalLibDependency();

		return dependency.getGroup();
	}

	@Input
	public String getOriginalLibModuleName() {
		return GradleUtil.toString(_originalLibModuleName);
	}

	@Input
	public String getOriginalLibModuleVersion() {
		Dependency dependency = getOriginalLibDependency();

		return dependency.getVersion();
	}

	@Input
	public String getOriginalLibSrcDirName() {
		return GradleUtil.toString(_originalLibSrcDirName);
	}

	public File getOriginalLibSrcFile() throws Exception {
		return FileUtil.get(_project, getOriginalLibSrcUrl());
	}

	public Map<String, File> getPatchedSrcDirMappings() {
		Map<String, File> patchedSrcDirMappings = new HashMap<>();

		for (Map.Entry<String, Object> entry :
				_patchedSrcDirMappings.entrySet()) {

			String extension = entry.getKey();
			File dir = GradleUtil.toFile(_project, entry.getValue());

			patchedSrcDirMappings.put(extension, dir);
		}

		return patchedSrcDirMappings;
	}

	@OutputFiles
	public FileCollection getPatchedSrcFiles() {
		Map<File, ConfigurableFileTree> patchedSrcFileTreeMap = new HashMap<>();

		for (String fileName : getFileNames()) {
			File patchedDir = getPatchedSrcDir(fileName);

			ConfigurableFileTree configurableFileTree =
				patchedSrcFileTreeMap.get(patchedDir);

			if (configurableFileTree == null) {
				configurableFileTree = _project.fileTree(patchedDir);

				patchedSrcFileTreeMap.put(patchedDir, configurableFileTree);
			}

			configurableFileTree.include(fileName);
		}

		Collection<ConfigurableFileTree> patchedSrcFileTrees =
			patchedSrcFileTreeMap.values();

		return _project.files(patchedSrcFileTrees.toArray());
	}

	public File getPatchesDir() {
		return GradleUtil.toFile(_project, _patchesDir);
	}

	@InputFiles
	@SkipWhenEmpty
	public FileCollection getPatchFiles() {
		if (!_patchFiles.isEmpty()) {
			return _project.files(_patchFiles);
		}
		else {
			return _project.fileTree(_patchesDir);
		}
	}

	public boolean isCopyOriginalLibClasses() {
		return _copyOriginalLibClasses;
	}

	@TaskAction
	public void patch() throws Exception {
		final File temporaryDir = getTemporaryDir();

		_project.delete(temporaryDir);

		temporaryDir.mkdir();

		Closure<Void> closure = new Closure<Void>(null) {

			@SuppressWarnings("unused")
			public void doCall(CopySpec copySpec) throws Exception {
				final String originalLibSrcDirName = getOriginalLibSrcDirName();

				if (!originalLibSrcDirName.equals(".")) {
					copySpec.eachFile(
						new Action<FileCopyDetails>() {

							@Override
							public void execute(
								FileCopyDetails fileCopyDetails) {

								RelativePath relativePath =
									fileCopyDetails.getRelativePath();

								String relativePathString =
									relativePath.getPathString();

								if (!relativePathString.startsWith(
										originalLibSrcDirName + "/")) {

									fileCopyDetails.exclude();

									return;
								}

								relativePathString =
									relativePathString.substring(
										originalLibSrcDirName.length() + 1);

								fileCopyDetails.setRelativePath(
									RelativePath.parse(
										true, relativePathString));
							}

						});
				}

				copySpec.filter(FixCrLfFilter.class);
				copySpec.from(_project.zipTree(getOriginalLibSrcFile()));
				copySpec.include(getFileNames());
				copySpec.into(temporaryDir);
				copySpec.setIncludeEmptyDirs(false);
			}

		};

		_project.copy(closure);

		for (final File patchFile : getPatchFiles()) {
			final ByteArrayOutputStream byteArrayOutputStream =
				new ByteArrayOutputStream();

			_project.exec(
				new Action<ExecSpec>() {

					@Override
					public void execute(ExecSpec execSpec) {
						execSpec.setExecutable("patch");
						execSpec.setWorkingDir(temporaryDir);

						execSpec.args("--binary");
						execSpec.args(
							"--input=" +
								FileUtil.relativize(patchFile, temporaryDir));
						execSpec.args("--strip=1");

						execSpec.setStandardOutput(byteArrayOutputStream);
					}

				});

			System.out.println(byteArrayOutputStream.toString());
		}

		FileTree fileTree = _project.fileTree(temporaryDir);

		for (File file : fileTree) {
			File patchedSrcDir = getPatchedSrcDir(file.getName());

			if (patchedSrcDir == null) {
				continue;
			}

			Path patchedSrcDirPath = patchedSrcDir.toPath();

			String relativePath = FileUtil.relativize(file, temporaryDir);

			patchedSrcDirPath = patchedSrcDirPath.resolve(relativePath);

			Files.createDirectories(patchedSrcDirPath.getParent());

			Files.move(
				file.toPath(), patchedSrcDirPath,
				StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public PatchTask patchedSrcDirMapping(String extension, Object dir) {
		_patchedSrcDirMappings.put(extension, dir);

		return this;
	}

	public PatchTask patchFiles(Iterable<Object> patchFiles) {
		GUtil.addToCollection(_patchFiles, patchFiles);

		return this;
	}

	public PatchTask patchFiles(Object ... patchFiles) {
		return patchFiles(Arrays.asList(patchFiles));
	}

	public void setCopyOriginalLibClasses(boolean copyOriginalLibClasses) {
		_copyOriginalLibClasses = copyOriginalLibClasses;
	}

	public void setFileNames(Iterable<Object> fileNames) {
		_fileNames.clear();

		GUtil.addToCollection(_fileNames, fileNames);
	}

	public void setOriginalLibConfigurationName(
		Object originalLibConfigurationName) {

		_originalLibConfigurationName = originalLibConfigurationName;
	}

	public void setOriginalLibModuleName(Object originalLibModuleName) {
		_originalLibModuleName = originalLibModuleName;
	}

	public void setOriginalLibSrcDirName(Object originalLibSrcDirName) {
		_originalLibSrcDirName = originalLibSrcDirName;
	}

	public void setPatchedSrcDirMappings(
		Map<String, Object> patchedSrcDirMappings) {

		_patchedSrcDirMappings.clear();

		_patchedSrcDirMappings.putAll(patchedSrcDirMappings);
	}

	public void setPatchesDir(Object patchesDir) {
		_patchesDir = patchesDir;
	}

	public void setPatchFiles(Iterable<Object> patchFiles) {
		_patchFiles.clear();

		GUtil.addToCollection(_patchFiles, patchFiles);
	}

	protected Dependency getOriginalLibDependency() {
		Configuration configuration = GradleUtil.getConfiguration(
			_project, getOriginalLibConfigurationName());

		ResolvableDependencies resolvableDependencies =
			configuration.getIncoming();

		String moduleName = getOriginalLibModuleName();

		for (Dependency dependency : resolvableDependencies.getDependencies()) {
			if (moduleName.equals(dependency.getName())) {
				return dependency;
			}
		}

		throw new GradleException("Unable to find original lib " + moduleName);
	}

	protected String getOriginalLibSrcUrl() {
		StringBuilder sb = new StringBuilder(_BASE_URL);

		String moduleGroup = getOriginalLibModuleGroup();

		sb.append(moduleGroup.replace('.', '/'));

		sb.append('/');
		sb.append(getOriginalLibModuleName());
		sb.append('/');
		sb.append(getOriginalLibModuleVersion());
		sb.append('/');
		sb.append(getOriginalLibModuleName());
		sb.append('-');
		sb.append(getOriginalLibModuleVersion());
		sb.append("-sources.jar");

		return sb.toString();
	}

	protected File getPatchedSrcDir(String fileName) {
		String extension = PATCHED_SRC_DIR_MAPPING_DEFAULT_EXTENSION;

		int pos = fileName.indexOf('.');

		if (pos != -1) {
			extension = fileName.substring(pos + 1);
		}

		Object patchedSrcDir = _patchedSrcDirMappings.get(extension);

		if (patchedSrcDir == null) {
			patchedSrcDir = _patchedSrcDirMappings.get(
				PATCHED_SRC_DIR_MAPPING_DEFAULT_EXTENSION);
		}

		return GradleUtil.toFile(_project, patchedSrcDir);
	}

	private static final String _BASE_URL =
		"http://repo.maven.apache.org/maven2/";

	private boolean _copyOriginalLibClasses = true;
	private final List<Object> _fileNames = new ArrayList<>();
	private Object _originalLibConfigurationName =
		JavaPlugin.COMPILE_CONFIGURATION_NAME;
	private Object _originalLibModuleName;
	private Object _originalLibSrcDirName = ".";
	private final Map<String, Object> _patchedSrcDirMappings = new HashMap<>();
	private Object _patchesDir = "patches";
	private final List<Object> _patchFiles = new ArrayList<>();
	private final Project _project;

}