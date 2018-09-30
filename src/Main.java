import com.mongodb.*;
import java.io.*;
import com.mongodb.client.*;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;
import java.util.ArrayList;

public class Main {

    private int totalTermsInClass[] = new int[2];
    private MongoCollection table;
    private MongoCollection tokenCollectionClassOne, tokenCollectionClassTwo, testDB, testTokenDBClassOne, testTokenDBClassTwo;
    private double class1Prior, class2Prior;

    private Main(){
        MongoDatabase db;
        db = new MongoClient( "localhost" , 27017 ).getDatabase("Vocabulary");
        table = db.getCollection("vocabulary");
        tokenCollectionClassOne = db.getCollection("tokenCollectionClassOne");
        tokenCollectionClassTwo = db.getCollection("tokenCollectionClassTwo");
        testDB = db.getCollection("testDB");
        testTokenDBClassOne = db.getCollection("testTokenDBClassOne");
        testTokenDBClassTwo = db.getCollection("testTokenDBClassTwo");
    }

    private void addTokens(int klas, MongoCollection tokenCollection, MongoCollection vocabularyCollection, boolean train){
        tokenCollection.drop();
        ArrayList a = new ArrayList();
        MongoCursor<Document> cursor = vocabularyCollection.find(eq("class", klas)).iterator();
        try {
            while (cursor.hasNext()) {
                for(String s : cursor.next().get("document").toString().split(" ")){
                    a.add(s);
                    if(tokenCollection.count(eq("token", s))==0){
                        Document d = new Document();
                        d.put("_id", s);
                        d.put("token", s);
                        d.put("count", 1);
                        d.put("class", klas);
                        tokenCollection.insertOne(d);
                    } else {
                        tokenCollection.updateOne(and(eq("_id", s), eq("token", s)), inc("count", 1));
                    }
                }
            }
        }
        catch(Exception e){
                System.out.println(e);
        }
        finally {
            cursor.close();
        }
        if(train){
            totalTermsInClass[klas] = a.size();
        }
    }

    private double computeConditionalProbability(int klas, MongoCollection tokenCollection){
        double termCount;
        double condProb = 1.0;
        double totalTermsInAllClasses;
        MongoCursor<Document> tokenCursor = tokenCollection.find().iterator();
            try {
                while (tokenCursor.hasNext()) {
                    Document d = tokenCursor.next();
                    totalTermsInAllClasses = tokenCollectionClassOne.count() + tokenCollectionClassTwo.count();
                    termCount = Double.parseDouble(d.get("count").toString());
                    condProb += (double)(termCount + 1.0) / ((double)totalTermsInClass[klas] + (double)totalTermsInAllClasses);
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                tokenCursor.close();
            }
            return condProb;
    }

    private String trainMultinomial() throws FileNotFoundException{
        pullData("/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/traindata.txt", "/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/trainlabels.txt", table);
        double classOneProb = 1.0, classTwoProb = 1.0;
        double classOneCount = table.count(eq("class", 0));
        double classTwoCount = table.count(eq("class", 1));
        class1Prior = (classOneCount / (classOneCount + classTwoCount));
        class2Prior = (classTwoCount / (classOneCount + classTwoCount));
        addTokens(0, tokenCollectionClassOne, table, true);
        addTokens(1, tokenCollectionClassTwo, table, true);
        classOneProb = computeConditionalProbability(0, tokenCollectionClassOne);
        classTwoProb = computeConditionalProbability(1, tokenCollectionClassTwo);
        String str = "Training Metrics: \n" +
                "Class 0: " + (classOneProb * class1Prior)*100 + "%\n" +
                "Class 1: " + (classTwoProb * class1Prior)*100 + "%\n";

        return str;
    }

    private String testMultinomial() throws IOException {
        pullData("/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/testdata.txt", "/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/testlabels.txt", testDB);
        addTokens(0, testTokenDBClassOne, testDB, false);
        addTokens(1, testTokenDBClassTwo, testDB, false);
        MongoCursor<Document> tokenCursor = testTokenDBClassOne.find().iterator();
        double classOneCondProb = computeConditionalProbability(0, testTokenDBClassOne);
        double classTwoCondProb = computeConditionalProbability(1, testTokenDBClassTwo);
        String str = "Testing Metrics: \n" +
                "Class 0: " + (classOneCondProb * class1Prior) * 100 + "%\n" +
                "Class 1: " + (classTwoCondProb * class2Prior) * 100 + "%\n";

        return str;
    }

    private void pullData(String path1, String path2, MongoCollection DB) throws FileNotFoundException{
        DB.drop();
        BufferedReader br1 = new BufferedReader(new FileReader(new File(path1)));
        BufferedReader br2 = new BufferedReader(new FileReader(new File(path2)));
        String s1,s2;
        try {
            while ((s1 = br1.readLine())!= null) {
                if((s2 = br2.readLine())!= null){
                    Document d = new Document();
                    d.put("document", s1);
                    d.put("class", Integer.parseInt(s2));
                    DB.insertOne(d);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void writeResults(String s) throws IOException{
        BufferedWriter writer = new BufferedWriter(new FileWriter("result.txt"));
        writer.write(s);
        writer.close();
    }

    public static void main(String[] args) throws Exception {
        Main obj = new Main();
        String results;
        results = obj.trainMultinomial();
        results += obj.testMultinomial();
        obj.writeResults(results);

    }
}
