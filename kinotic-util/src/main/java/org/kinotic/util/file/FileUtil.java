/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.util.file;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;

/**
 *
 * Created by navid on 9/16/19
 */
public class FileUtil {

    /**
     * Copies the lines in a file reversely (similar to a BufferedReader, but starting at the last line). Useful for e.g
     * @param sourceFile to copy to the destinationFle
     * @param destinationFile where the file will be copied
     * @throws IOException if an error occurs during copying
     */
    public static void copyFileLinesInReverse(File sourceFile, File destinationFile) throws IOException {
        try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destinationFile))) {
            copyFileLinesInReverse(sourceFile, bos);
        }
    }


    /**
     * Copies the lines in a file reversely (similar to a BufferedReader, but starting at the last line). Useful for e.g
     * @param sourceFile to copy to the destinationFle
     * @param destinationStream where the file will be copied
     * @throws IOException if an error occurs during copying
     */
    public static void copyFileLinesInReverse(File sourceFile, OutputStream destinationStream) throws IOException {
        Charset charset = Charset.defaultCharset();
        try(ReversedLinesFileReader fileReader = ReversedLinesFileReader.builder()
                .setPath(sourceFile.toPath())
                .setCharset(charset)
                .get()) {
            String line = fileReader.readLine();
            while (line != null) {
                //noinspection StringConcatenationInLoop
                line = line + "\n";
                destinationStream.write(line.getBytes(charset));
                line = fileReader.readLine();
            }
        }
    }

    /**
     * Copy the given file in to the destination reversing all the bytes
     * @param sourceFile to copy to the destinationFle
     * @param destinationFile where the file will be copied
     * @throws IOException if an error occurs during copying
     */
    public static void copyFileInReverse(File sourceFile, File destinationFile) throws IOException {
        try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destinationFile))) {
            copyFileInReverse(sourceFile, bos);
        }
    }

    /**
     * Copy the given file in to the destination reversing all the bytes
     * @param sourceFile to copy to the destinationFle
     * @param destinationStream where the file will be copied
     * @throws IOException if an error occurs during copying
     */
    public static void copyFileInReverse(File sourceFile, OutputStream destinationStream) throws IOException {
        byte[] buffer = new byte[1024];
        try(RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {

            long bytesLeftToCopy = raf.length();
            long offset = bytesLeftToCopy;

            while (bytesLeftToCopy > 0) {
                int lenBytesToCopy = bytesLeftToCopy > 1024 ? 1024 : (int) bytesLeftToCopy;
                offset -= lenBytesToCopy;

                raf.seek(offset);
                int bytesRead = raf.read(buffer, 0, lenBytesToCopy);
                // sanity check,
                if (bytesRead != lenBytesToCopy) {
                    throw new IllegalStateException("Not all bytes could be copied");
                }

                ArrayUtils.reverse(buffer);
                int arrayOffset = 1024 - lenBytesToCopy;
                destinationStream.write(buffer, arrayOffset, bytesRead);

                bytesLeftToCopy -= bytesRead;
            }
        }
    }


    public static Path moveWithRetry(Path source, Path target, int tries) throws IOException{
        Path ret;
        int tried = 0;
        while(true){
            try {
                tried++;
                // create any non existent parent directories
                Files.createDirectories(target.getParent());

                ret = Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                break;
            } catch (IOException e) {
                if(tried >= tries){
                    throw e;
                }else{
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        // we ignore this
                    }
                }
            }
        }
        return ret;
    }

    public static Path moveWithRetry(String source, String target, int tries) throws IOException{
        return FileUtil.moveWithRetry(Paths.get(source),Paths.get(target),tries);
    }

    public static void handleFailed(Path sourceDirectory,
                                    Path targetDirectory,
                                    Path sourceFile,
                                    Exception e) throws IOException{

        Path relativePath = sourceDirectory.relativize(sourceFile);
        Path targetPath = targetDirectory.resolve("failed").resolve(relativePath);
        Path logPath =  Paths.get(targetPath + ".error.log");

        // create any non existent parent directories
        // this is done here as well so the log can be written
        Files.createDirectories(targetPath.getParent());

        // write stack to log
        Files.write(logPath, ExceptionUtils.getStackTrace(e).getBytes(), StandardOpenOption.CREATE_NEW);

        // move to failed path
        FileUtil.moveWithRetry(sourceFile, targetPath, 4);
    }

    public static void handleSuccessWithMove(Path sourceDirectory,
                                             Path targetDirectory,
                                             Path sourceFile) throws IOException {
        Path relativePath = sourceDirectory.relativize(sourceFile);
        Path targetPath = targetDirectory.resolve("success").resolve(relativePath);

        FileUtil.moveWithRetry(sourceFile, targetPath, 4);
    }

}
