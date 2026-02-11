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

package org.kinotic.util;

import org.kinotic.util.file.FileUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 *
 * Created by Navid Mitchell 🤪 on 2/18/21.
 */
public class FileUtilTests {

    @Test
    public void testReverseCopySmall() throws Exception{
        File smallFile = loadFile("testData/testFile700IshBytes.txt");

        byte[] actual = copyFileToBytesInternal(smallFile);
        byte[] expected = copyFileToBytesAndReverseJdkSlow(smallFile);

        Assertions.assertArrayEquals(expected, actual);
    }

    @Test
    public void testReverseCopyMedium() throws Exception{
        File smallFile = loadFile("testData/testFile1024Bytes.txt");

        byte[] actual = copyFileToBytesInternal(smallFile);
        byte[] expected = copyFileToBytesAndReverseJdkSlow(smallFile);

        Assertions.assertArrayEquals(expected, actual);
    }

    @Test
    public void testReverseCopyLarge() throws Exception{
        File smallFile = loadFile("testData/testFile5000ishBytes.txt");

        byte[] actual = copyFileToBytesInternal(smallFile);
        byte[] expected = copyFileToBytesAndReverseJdkSlow(smallFile);

        Assertions.assertArrayEquals(expected, actual);
    }

    private byte[] copyFileToBytesInternal(File sourceFile) throws IOException{
        ByteArrayOutputStream byos = new ByteArrayOutputStream(1024);
        FileUtil.copyFileInReverse(sourceFile, byos);
        return byos.toByteArray();
    }

    private byte[] copyFileToBytesAndReverseJdkSlow(File sourceFile) throws IOException {
        ByteArrayOutputStream byos = new ByteArrayOutputStream(1024);
        try (FileInputStream fis = new FileInputStream(sourceFile)) {
            fis.transferTo(byos);
        }
        byte[] bytes = byos.toByteArray();
        ArrayUtils.reverse(bytes);
        return bytes;
    }

    private File loadFile(String resourceName){
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(resourceName).getFile());
    }

}
