import java.util.ArrayList;
import java.util.List;

public class Site {

    private List<Question> questions;
    private String siteName;

//    public Site(ArrayList<Question> questions, String siteName) {
//        this.questions = questions;
//        this.siteName = siteName;
//    }


    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }
}
