package com.dium.demo.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private FileService fileService;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @BeforeEach
    void setUp() {
        fileService = new FileService("dummyCloudName", "dummyApiKey", "dummyApiSecret");

        ReflectionTestUtils.setField(fileService, "cloudinary", cloudinary);
    }


    @Test
    void saveFile_WithValidImage_UploadsAndReturnsUrl() throws IOException {
        byte[] fileContent = "dummy image data".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", fileContent
        );
        String expectedUrl = "http://res.cloudinary.com/demo/image/upload/v1234/dium_uploads/image.jpg";

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("url", expectedUrl));

        String actualUrl = fileService.saveFile(file);

        assertThat(actualUrl).isEqualTo(expectedUrl);
        verify(cloudinary).uploader();
        verify(uploader).upload(eq(fileContent), argThat(map -> map.containsValue("dium_uploads")));
    }

    @Test
    void saveFile_WithNullFile_ReturnsNull() throws IOException {
        String result = fileService.saveFile(null);

        assertThat(result).isNull();
        verifyNoInteractions(cloudinary);
    }

    @Test
    void saveFile_WithEmptyFile_ReturnsNull() throws IOException {
        MultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);

        String result = fileService.saveFile(emptyFile);

        assertThat(result).isNull();
        verifyNoInteractions(cloudinary);
    }

    @Test
    void saveFile_WithNonImageFile_ThrowsRuntimeException() {
        MultipartFile textFile = new MockMultipartFile(
                "file", "document.txt", "text/plain", "text data".getBytes()
        );

        assertThatThrownBy(() -> fileService.saveFile(textFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Only images are allowed");

        verifyNoInteractions(cloudinary);
    }

    @Test
    void saveFile_WithoutContentType_ThrowsRuntimeException() {
        MultipartFile fileWithoutType = new MockMultipartFile(
                "file", "document.txt", null, "data".getBytes()
        );

        assertThatThrownBy(() -> fileService.saveFile(fileWithoutType))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Only images are allowed");
    }

    @Test
    void deleteFile_WithValidUrl_CallsDestroy() throws IOException {
        String url = "http://res.cloudinary.com/demo/image/upload/v161234/dium_uploads/test-image.jpg";
        when(cloudinary.uploader()).thenReturn(uploader);

        fileService.deleteFile(url);

        verify(uploader).destroy(eq("dium_uploads/test-image"), anyMap());
    }

    @Test
    void deleteFile_WithNullUrl_DoesNothing() {
        fileService.deleteFile(null);

        verifyNoInteractions(cloudinary);
    }

    @Test
    void deleteFile_WhenUploaderThrowsException_HandlesGracefully() throws IOException {
        String url = "http://res.cloudinary.com/demo/image/upload/v161234/dium_uploads/test.jpg";
        when(cloudinary.uploader()).thenReturn(uploader);

        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IOException("Cloudinary API is down"));

        fileService.deleteFile(url);

        verify(uploader).destroy(eq("dium_uploads/test"), anyMap());
    }


    @Test
    void extractPublicId_WithValidUrl_ReturnsCorrectId() {
        String url = "http://res.cloudinary.com/demo/image/upload/v161234/folder/subfolder/my_photo.png";

        String publicId = fileService.extractPublicId(url);

        assertThat(publicId).isEqualTo("dium_uploads/my_photo");
    }

    @Test
    void extractPublicId_WithUrlWithoutExtension_ReturnsCorrectId() {

        String url = "http://res.cloudinary.com/demo/image/upload/v161234/folder/my_photo_without_extension";

        String publicId = fileService.extractPublicId(url);

        assertThat(publicId).isNull();
    }
}