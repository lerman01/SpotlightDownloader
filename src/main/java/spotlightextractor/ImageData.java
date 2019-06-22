package spotlightextractor;

public class ImageData {

    private String id;
    private String url;
    private String description;


    public ImageData(String id, String url, String description) {
        this.id = id;
        this.url = url;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "ImageData{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
