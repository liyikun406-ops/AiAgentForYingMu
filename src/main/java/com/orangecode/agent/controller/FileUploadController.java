package com.orangecode.agent.controller;

import com.orangecode.agent.config.FileUploadConfig;
import com.orangecode.agent.response.ApiResponse;
import com.orangecode.agent.response.FileUploadResult;
import com.orangecode.agent.service.VectorIndexService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


@Controller
@RequestMapping("/file")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Resource
    private FileUploadConfig fileUploadConfig;
    @Resource
    private VectorIndexService vectorIndexService;

    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty()){
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        // 检查文件名是否为空
        String originalFileName = file.getOriginalFilename();
        if(originalFileName == null || originalFileName.isEmpty()){
            return ResponseEntity.badRequest().body("文件名不能为空");
        }

        // 通过文件后缀检查格式
        String fileExtension = getFileExtension(originalFileName);
        if(!isAllowedExtension(fileExtension)){
            return ResponseEntity.badRequest().body("文件格式不支持");
        }
        try{
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();

            // 查询目录是否存在
            if(!Files.exists(uploadDir)){
                Files.createDirectories(uploadDir);
            }
            Path filePath = uploadDir.resolve(uploadPath);

            // 查询文件是否存在
            if(Files.exists(filePath)){
                Files.delete(filePath);
            }

            Files.copy(file.getInputStream(), filePath);

            logger.info("文件上传成功");

            // 文件上传成功后自动插入向量库

            try{
                vectorIndexService.indexSingleFile(filePath.toString());
            } catch (Exception e) {
                logger.error("向量索引创建失败: {}, 错误: {}", filePath, e.getMessage(), e);
                // 注意：即使索引失败，文件上传仍然成功，只是记录错误日志
                // 可以根据业务需求决定是否要删除文件或返回错误
            }

            FileUploadResult response = new FileUploadResult(
                    originalFileName,
                    filePath.toString(),
                    file.getSize()
            );

            // 使用统一的API响应格式
            ApiResponse<FileUploadResult> apiResponse = new ApiResponse<>();
            apiResponse.setCode(200);
            apiResponse.setMessage("success");
            apiResponse.setData(response);

            return ResponseEntity.ok(apiResponse);
        } catch (IOException e) {
            ApiResponse<String> errorResponse = new ApiResponse<>();
            errorResponse.setCode(500);
            errorResponse.setMessage("文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }

    }

    private String getFileExtension(String fileOrigionalName){
        String[] parts = fileOrigionalName.split(".");
        return parts[parts.length - 1];
    }

    private boolean isAllowedExtension(String fileExtension){
        List<String> allowedList = Arrays.asList(fileUploadConfig.getAllowedExtensions().split(","));

        return allowedList.contains(fileExtension);
    }

}
