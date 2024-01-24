package com.megvii.sc;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.util.HashMap;
import java.util.UUID;

@SpringBootApplication
@Slf4j
public class TestApplication {

    @Value("${filepath}")
    private String filepath;

    @Resource
    private TransferManager transferManager;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic}")
    private String kafkaTopic;

    @Value("${bucketname}")
    private String bucketName;

    private String gspUri;


    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TestApplication.class, args);
        TestApplication t = context.getBean(TestApplication.class);
        System.out.println("it's begin");
        t.method();
    }


    public void method() {
        String folderPath = filepath;
        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        printFolderContents(folder);
        log.info("it's done");
    }
    private void printFolderContents(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                File gspFile = null;
                File jsonFile = null;
                for (File file : files) {
                    if (file.isDirectory()) {
                        printFolderContents(file); // 递归处理文件夹
                    } else {
                        String fileName = file.getName();
                        if (fileName.endsWith(".jpg")) {
                            gspFile = file;
                        } else if(fileName.endsWith("aipaas_event_info.json") ) {
                            jsonFile = file;
                        }
                    }
                }
                if(gspFile!=null && jsonFile!=null) {
                    String gspUriAddress = sendToGsp(gspFile);
                    modifyJson(jsonFile,gspUriAddress);
                    sendToKafka(jsonFile);
                } else if (gspFile == null && jsonFile!=null) {
                    log.error("In {},Lose .jpg文件", folder);
                } else if(jsonFile == null && gspFile!=null) {
                    log.error("In {} ,Lose aipaas_event_info.json", folder);
                }
            }
        } else {
            log.info("error file path ");// 打印文件夹本身的名称
        }
        //对jsonFile进行修改
    }

    private void sendToKafka(File file) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObj = null;
            try {
                jsonObj = (JSONObject) parser.parse(new FileReader(file));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            // 发送消息
            kafkaTemplate.send(kafkaTopic, jsonObj.toJSONString());
            log.info("{},send to kafka success", file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void modifyJson(File file, String address) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObj = (JSONObject) parser.parse(new FileReader(file));

            //  _id 修改为 id
            if(jsonObj.get("_id") != null) {
                String id = jsonObj.get("_id").toString();
                jsonObj.remove("_id");
                jsonObj.put("id",id);
            }
            //去除saasInfo
            if(jsonObj.get("saasInfo") !=null) {
                jsonObj.remove("saasInfo");
            }
            HashMap fileData = (HashMap) jsonObj.get("data");
            //去除labels
            JSONArray alarmEvents = (JSONArray) fileData.get("alarmEvents");
            if(((JSONObject) alarmEvents.get(0)).get("labels") !=null) {
                ((JSONObject) alarmEvents.get(0)).remove("labels");
            }
            //更新gsp地址
            fileData.put("uri",address);

            // 写回 JSON 文件
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(jsonObj.toJSONString());
            fileWriter.close();
            log.info(" Already Modify : {} ", file);
        } catch (Exception e) {
            log.error("{} occur error", file);
        }
    }

    private String sendToGsp(File file) {
        String key = UUID.randomUUID().toString();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(file.length());
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Upload upload = this.transferManager.upload(bucketName,key, input, objectMetadata);
        try {
            upload.waitForCompletion();
            upload.waitForUploadResult();
            if("Completed".equals(upload.getState().name())) {
                log.info("upload gsp success ,gspUri: {}", "gsp://" + bucketName + "/" + key);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return null;
        } finally {
            try {
                if (null != input) {
                    input.close();
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return "gsp://" + bucketName + "/" + key;
    }
}
