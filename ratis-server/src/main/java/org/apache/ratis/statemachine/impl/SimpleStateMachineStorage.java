/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.statemachine.impl;

import org.apache.ratis.io.MD5Hash;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.apache.ratis.statemachine.SnapshotRetentionPolicy;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.thirdparty.com.google.common.annotations.VisibleForTesting;
import org.apache.ratis.util.AtomicFileOutputStream;
import org.apache.ratis.util.FileUtils;
import org.apache.ratis.util.MD5FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A StateMachineStorage that stores the snapshot in a single file.
 */
public class SimpleStateMachineStorage implements StateMachineStorage {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleStateMachineStorage.class);

  static final String SNAPSHOT_FILE_PREFIX = "snapshot";
  static final String CORRUPT_SNAPSHOT_FILE_SUFFIX = ".corrupt";
  /** snapshot.term_index */
  public static final Pattern SNAPSHOT_REGEX =
      Pattern.compile(SNAPSHOT_FILE_PREFIX + "\\.(\\d+)_(\\d+)");

  private volatile File stateMachineDir = null;

  private volatile SingleFileSnapshotInfo currentSnapshot = null;

  @Override
  public void init(RaftStorage storage) throws IOException {
    this.stateMachineDir = storage.getStorageDir().getStateMachineDir();
    loadLatestSnapshot();
  }

  @Override
  public void format() throws IOException {
    // TODO
  }

  static List<SingleFileSnapshotInfo> getSingleFileSnapshotInfos(Path dir) throws IOException {
    final List<SingleFileSnapshotInfo> infos = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path path : stream) {
        final Path filename = path.getFileName();
        if (filename != null) {
          final Matcher matcher = SNAPSHOT_REGEX.matcher(filename.toString());
          if (matcher.matches()) {
            final long term = Long.parseLong(matcher.group(1));
            final long index = Long.parseLong(matcher.group(2));
            final FileInfo fileInfo = new FileInfo(path, null); //No FileDigest here.
            infos.add(new SingleFileSnapshotInfo(fileInfo, term, index));
          }
        }
      }
    }
    return infos;
  }

  @Override
  public void cleanupOldSnapshots(SnapshotRetentionPolicy snapshotRetentionPolicy) throws IOException {
    if (stateMachineDir == null) {
      return;
    }

    final int numSnapshotsRetained = Optional.ofNullable(snapshotRetentionPolicy)
        .map(SnapshotRetentionPolicy::getNumSnapshotsRetained)
        .orElse(SnapshotRetentionPolicy.DEFAULT_ALL_SNAPSHOTS_RETAINED);
    if (numSnapshotsRetained <= 0) {
      return;
    }

    final List<SingleFileSnapshotInfo> allSnapshotFiles = getSingleFileSnapshotInfos(stateMachineDir.toPath());

    if (allSnapshotFiles.size() > snapshotRetentionPolicy.getNumSnapshotsRetained()) {
      allSnapshotFiles.sort(Comparator.comparing(SnapshotInfo::getIndex).reversed());
      List<File> snapshotFilesToBeCleaned = allSnapshotFiles.subList(
              snapshotRetentionPolicy.getNumSnapshotsRetained(), allSnapshotFiles.size()).stream()
          .map(singleFileSnapshotInfo -> singleFileSnapshotInfo.getFile().getPath().toFile())
          .collect(Collectors.toList());
      for (File snapshotFile : snapshotFilesToBeCleaned) {
        LOG.info("Deleting old snapshot at {}", snapshotFile.getAbsolutePath());
        FileUtils.deleteFileQuietly(snapshotFile);
      }
    }
  }

  public static TermIndex getTermIndexFromSnapshotFile(File file) {
    final String name = file.getName();
    final Matcher m = SNAPSHOT_REGEX.matcher(name);
    if (!m.matches()) {
      throw new IllegalArgumentException("File \"" + file
          + "\" does not match snapshot file name pattern \""
          + SNAPSHOT_REGEX + "\"");
    }
    final long term = Long.parseLong(m.group(1));
    final long index = Long.parseLong(m.group(2));
    return TermIndex.valueOf(term, index);
  }

  protected static String getTmpSnapshotFileName(long term, long endIndex) {
    return getSnapshotFileName(term, endIndex) + AtomicFileOutputStream.TMP_EXTENSION;
  }

  protected static String getCorruptSnapshotFileName(long term, long endIndex) {
    return getSnapshotFileName(term, endIndex) + CORRUPT_SNAPSHOT_FILE_SUFFIX;
  }

  public File getSnapshotFile(long term, long endIndex) {
    final File dir = Objects.requireNonNull(stateMachineDir, "stateMachineDir == null");
    return new File(dir, getSnapshotFileName(term, endIndex));
  }

  protected File getTmpSnapshotFile(long term, long endIndex) {
    final File dir = Objects.requireNonNull(stateMachineDir, "stateMachineDir == null");
    return new File(dir, getTmpSnapshotFileName(term, endIndex));
  }

  protected File getCorruptSnapshotFile(long term, long endIndex) {
    final File dir = Objects.requireNonNull(stateMachineDir, "stateMachineDir == null");
    return new File(dir, getCorruptSnapshotFileName(term, endIndex));
  }

  static SingleFileSnapshotInfo findLatestSnapshot(Path dir) throws IOException {
    final Iterator<SingleFileSnapshotInfo> i = getSingleFileSnapshotInfos(dir).iterator();
    if (!i.hasNext()) {
      return null;
    }

    SingleFileSnapshotInfo latest = i.next();
    for(; i.hasNext(); ) {
      final SingleFileSnapshotInfo info = i.next();
      if (info.getIndex() > latest.getIndex()) {
        latest = info;
      }
    }

    // read md5
    final Path path = latest.getFile().getPath();
    final MD5Hash md5 = MD5FileUtil.readStoredMd5ForFile(path.toFile());
    final FileInfo info = new FileInfo(path, md5);
    return new SingleFileSnapshotInfo(info, latest.getTerm(), latest.getIndex());
  }

  public void loadLatestSnapshot() throws IOException {
    if (stateMachineDir == null) {
      return;
    }
    this.currentSnapshot = findLatestSnapshot(stateMachineDir.toPath());
  }

  public static String getSnapshotFileName(long term, long endIndex) {
    return SNAPSHOT_FILE_PREFIX + "." + term + "_" + endIndex;
  }

  @Override
  public SingleFileSnapshotInfo getLatestSnapshot() {
    return currentSnapshot;
  }

  @VisibleForTesting
  File getStateMachineDir() {
    return stateMachineDir;
  }
}
