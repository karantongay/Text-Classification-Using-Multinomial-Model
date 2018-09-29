import com.mongodb.*;

import java.io.*;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.AggregateIterable;
import static com.mongodb.client.model.Filters.*;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import static com.mongodb.client.model.Updates.*;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private int totalTermsInClass[] = new int[2];
    private BigDecimal condProb = new BigDecimal(BigInteger.ONE, MathContext.DECIMAL64);
    private BigDecimal classOneProb, classTwoProb;

    private int addTokens(int klas, MongoCollection tokenCollection, MongoCollection vocabularyCollection){
        tokenCollection.drop();
        ArrayList a = new ArrayList();
        MongoCursor<Document> cursor = vocabularyCollection.find(eq("class", klas)).iterator();
        try {
            while (cursor.hasNext()) {
                int counter = 0;
                for(String s : cursor.next().get("document").toString().split(" ")){
                    a.add(s);
                    counter++;
                    Document d = new Document();
                    d.put("token", s);
                    d.put("count", counter);
                    d.put("class", klas);
                    tokenCollection.insertOne(d);
//                    tokenCollection.updateOne(eq("token", s), new Document("$set", d), new UpdateOptions().upsert(true));
                }
            }
        }
        catch(Exception e){
                System.out.println(e);
        }
        finally {
            cursor.close();
        }
        System.out.println(a.size());
        return a.size();
    }

    private BigDecimal computeConditionalProbability(int klas, MongoCollection tokenCollection){
        long termCount, totalTermsInAllClasses;
        MongoCursor<Document> tokenCursor = tokenCollection.find(eq("class", klas)).iterator();
            BigDecimal b;
            try {
                while (tokenCursor.hasNext()) {
                    for (String s : tokenCursor.next().get("token").toString().split("")) {
                        termCount = Integer.parseInt(tokenCursor.next().get("count").toString());
                        totalTermsInAllClasses = tokenCollection.count();
                        b = new BigDecimal((termCount + 1.0) / (totalTermsInClass[klas] + totalTermsInAllClasses), MathContext.DECIMAL64);
                        condProb = condProb.multiply(b, MathContext.DECIMAL64);
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                tokenCursor.close();
            }
            return condProb;
    }

    private void trainMultinomial() throws FileNotFoundException{
        MongoClient mongo = new MongoClient( "localhost" , 27017 );
        MongoDatabase db = mongo.getDatabase("Vocabulary");

        BufferedReader br1 = new BufferedReader(new FileReader(new File("/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/traindata.txt")));
        BufferedReader br2 = new BufferedReader(new FileReader(new File("/Users/karantongay/UVic/CSC 578 D Data Mining/Assignment 1/trainlabels.txt")));

        String s1,s2;
        MongoCollection table = db.getCollection("vocabulary");
        MongoCollection tokenCollectionClassOne = db.getCollection("tokenCollectionClassOne");
        MongoCollection tokenCollectionClassTwo = db.getCollection("tokenCollectionClassTwo");
        table.drop();

        try {
            while ((s1 = br1.readLine())!= null) {
                if((s2 = br2.readLine())!= null){
                    Document d = new Document();
                    d.put("document", s1);
                    d.put("class", Integer.parseInt(s2));
                    table.insertOne(d);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        Block<Document> printBlock = new Block<Document>() {
            @Override
            public void apply(final Document document) {
                System.out.println(document.toJson());
            }
        };

        double classOneCount = table.count(eq("class", 0));
        double classTwoCount = table.count(eq("class", 1));

        double class1Prior = (classOneCount / (classOneCount + classTwoCount));
        double class2Prior = (classTwoCount / (classOneCount + classTwoCount));

//        condProb = (termCount + 1) / (totalDistinctTerms + totalTermsInAllClasses)

        totalTermsInClass[0] = addTokens(0, tokenCollectionClassOne, table);
        totalTermsInClass[1] = addTokens(1, tokenCollectionClassTwo, table);

        classOneProb = computeConditionalProbability(0, tokenCollectionClassOne);
        classTwoProb = computeConditionalProbability(1, tokenCollectionClassTwo);

        BigDecimal ch1 = classOneProb.multiply(new BigDecimal(class1Prior, MathContext.DECIMAL64));

        System.out.println(ch1.round(MathContext.DECIMAL32));
        System.out.println(classTwoProb.multiply(new BigDecimal(class2Prior, MathContext.DECIMAL64)));
    }

    public static void main(String[] args) throws Exception {

        Main obj = new Main();
        obj.trainMultinomial();

    }
}
