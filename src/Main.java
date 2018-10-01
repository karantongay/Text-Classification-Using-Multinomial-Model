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
    private MongoCollection[] tokenCollection = new MongoCollection[2];
    private MongoCollection testDB;
    private double[] prior = new double[2];

    private Main(){
        MongoDatabase db;
        db = new MongoClient( "localhost" , 27017 ).getDatabase("Vocabulary");
        table = db.getCollection("vocabulary");
        tokenCollection[0] = db.getCollection("tokenCollectionClassOne");
        tokenCollection[1] = db.getCollection("tokenCollectionClassTwo");
        testDB = db.getCollection("testDB");
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

    private double computeConditionalProbability(MongoCollection docCollection){
        double termCount;
        double condProb[] = {1.0,1.0};
        double totalTermsInAllClasses;
        double accuracy = 0;
        String docClass = "";
        MongoCursor<Document> docCursor = docCollection.find().iterator();
        while(docCursor.hasNext()){
            Document d = docCursor.next();
            for(String s: d.get("document").toString().split(" ")){
                for(int i=0;i<2;i++){
                    MongoCursor<Document> t = tokenCollection[i].find(eq("token", s)).iterator();
                    if(t.hasNext()){
                        termCount = Double.parseDouble(t.next().get("count").toString());
                    } else {
                        termCount = 0;
                    }
                    totalTermsInAllClasses = tokenCollection[0].count() + tokenCollection[1].count();
                    condProb[i] *= ((double)(termCount + 1.0) / ((double)totalTermsInClass[i] + (double)totalTermsInAllClasses));
                }
            }
            condProb[0] *= prior[0];
            condProb[1] *= prior[1];
            if(condProb[0] > condProb[1]){
                docClass = "0";
            } else{
                docClass = "1";
            }
            if(docClass.equals(d.get("class").toString())){
                accuracy++;
            }
            condProb[0] = 1.0;
            condProb[1] = 1.0;
        }
        return (accuracy/docCollection.count())*100;
    }

    private String trainMultinomial() throws FileNotFoundException {
        pullData("traindata.txt", "trainlabels.txt", table);
        double classOneCount = table.count(eq("class", 0));
        double classTwoCount = table.count(eq("class", 1));
        prior[0] = (classOneCount / (classOneCount + classTwoCount));
        prior[1] = (classTwoCount / (classOneCount + classTwoCount));
        System.out.println("Prior -> " + prior[0] + " = " + prior[1]);
        addTokens(0, tokenCollection[0], table, true);
        addTokens(1, tokenCollection[1], table, true);
        String str = "Training Data Metrics (Training files: traindata.txt, trainlabels.txt): \n" +
                "Accuracy: " + computeConditionalProbability(table) + "%\n";

        return str;
    }

    private String testMultinomial() throws IOException {
        pullData("testdata.txt", "testlabels.txt", testDB);
        String str = "Testing Metrics (Testing files: testdata.txt, testlabels.txt): \n" +
                "Accuracy: " + computeConditionalProbability(testDB) + "%\n";

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
