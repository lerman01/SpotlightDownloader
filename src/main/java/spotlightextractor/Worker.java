package spotlightextractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovisionaries.i18n.LocaleCode;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;


public class Worker implements Runnable {

    private static final Logger logger = LogManager.getLogger(Worker.class);

    private static final String URL = "https://arc.msn.com/v3/Delivery/Cache?pid=%s&ctry=%s&lc=%s&fmt=json&lo=%s";
    private static final List<String> PID_LIST = Arrays.asList("209567", "279978", "209562");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36";
    private static final Random randomGenerator = new Random();
    private static final String IMAGES_FOLDER = "images";
    private static final String JPG = "JPG";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LocaleCode[] LOCALE_LIST = LocaleCode.values();

    private static List<File> imagesList;
    private CloseableHttpClient httpclient;

    Worker(CloseableHttpClient httpclient) {
        this.httpclient = httpclient;
    }

    public static void initImageList() {
        imagesList = new ArrayList<>();
        File dir = new File(IMAGES_FOLDER);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File file : directoryListing) {
                if (FilenameUtils.getExtension(file.getName()).toUpperCase().equals(JPG)) {
                    imagesList.add(file);
                }
            }
        }
    }

    public static List<File> getImageList() {
        return imagesList;
    }

    private static LocaleCode getRandomLocale() {
        LocaleCode localeCode;
        do {
            localeCode = LOCALE_LIST[randomGenerator.nextInt(LOCALE_LIST.length)];
        } while (localeCode.getCountry() == null || localeCode.getCountry().getName().equals("Undefined"));
        return localeCode;
    }

    public void run() {
        fetchData();
    }

    private void fetchData() {
        try {
            ImageData imageData = fetchImageData();
            if (imageData != null) {
                byte[] imageBytes = fetchImage(imageData);
                if (!saveImageIfNotExists(imageData, imageBytes)) {
                    logger.info(String.format("New image added : %s", imageData));
                } else {
                    logger.debug(String.format("File already exists : %s", imageData));
                }
            }
        } catch (Exception e) {
            logger.error("Error while get image", e);
        }
    }

    private ImageData fetchImageData() throws Exception {
        CloseableHttpResponse response = null;
        String imageUrl = null;
        String imageDescription = null;
        String imageId = null;
        HttpEntity entity1;
        String body = null;
        boolean ignoreRequest = false;
        try {
            LocaleCode randomLocale = getRandomLocale();
            HttpGet httpGet = new HttpGet(String.format(URL, PID_LIST.get(randomGenerator.nextInt(PID_LIST.size())), randomLocale.getCountry().toString().toLowerCase(), randomLocale.getLanguage().toString().toLowerCase(), randomGenerator.nextInt(900000) + 100000));
            httpGet.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
            response = httpclient.execute(httpGet);
            entity1 = response.getEntity();
            body = IOUtils.toString(entity1.getContent(), "UTF-8");
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new Exception(String.format("Status Code Error: %s"));
            }
            String item = null;
            JsonNode batchrsp = OBJECT_MAPPER.readTree(body).get("batchrsp");
            JsonNode errors = batchrsp.get("errors");
            if (errors != null) {
                int errorCode = errors.get(0).get("code").asInt();
                if (errorCode == 2040 || errorCode == 2000) {
                    ignoreRequest = true;
                } else {
                    throw new Exception(String.format("Error in response: %s", body));
                }
            }
            if (!ignoreRequest) {
                item = OBJECT_MAPPER.readTree(body).get("batchrsp").get("items").get(0).get("item").asText();
                JsonNode jsonNode = OBJECT_MAPPER.readTree(item);
                imageUrl = jsonNode.get("ad").get("image_fullscreen_001_landscape").get("u").asText();
                if (imageUrl.lastIndexOf("?") != -1) {
                    imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1, imageUrl.lastIndexOf("?"));
                } else {
                    imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                }
                jsonNode = jsonNode.get("ad").get("title_text");
                if (jsonNode != null) {
                    imageDescription = jsonNode.get("tx").asText();
                } else {
                    imageDescription = imageId;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new Exception(String.format("Error while fetching image data (response: %s)", body), e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error(String.format("Error while close response: %s", response.getStatusLine()));
                }
            }
        }
        return new ImageData(imageId, imageUrl, imageDescription);
    }

    private byte[] fetchImage(ImageData imageData) throws Exception {
        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(imageData.getUrl());
            response = httpclient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error(String.format("Fail to retrive image content: %s", response.getStatusLine()));
            }
            HttpEntity entity = response.getEntity();
            return entity.getContent().readAllBytes();
        } catch (Exception e) {
            logger.error(String.format("Error while getting image: %s", imageData));
            throw e;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error(String.format("Error while try close response: %s", response.getStatusLine()));
                }
            }
        }
    }

    private synchronized boolean saveImageIfNotExists(ImageData imageData, byte[] newImageBytes) throws IOException {
        for (int i = 0; i < imagesList.size(); i++) {
            FileInputStream fileInputStream = new FileInputStream(imagesList.get(i));
            byte[] imageBytes = IOUtils.toByteArray(fileInputStream);
            if (Arrays.equals(imageBytes, newImageBytes)) {
                fileInputStream.close();
                return true;
            }
            fileInputStream.close();
        }
        File nextFile = getNextFile(imageData.getDescription());
        Files.copy(new ByteArrayInputStream(newImageBytes), nextFile.toPath());
        imagesList.add(nextFile);
        Main.addWorkers();
        return false;
    }

    private File getNextFile(String filename) {
        File file = new File("images/" + filename + ".jpg");
        Integer fileNo = 1;
        while (file.exists()) {
            file = new File("images/" + filename + fileNo.toString() + ".jpg");
            fileNo++;
        }
        return file;
    }
}

