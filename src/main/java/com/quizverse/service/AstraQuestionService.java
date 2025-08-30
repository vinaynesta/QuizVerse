package com.quizmailer.service;

import com.datastax.astra.client.DataAPIClient;
import com.datastax.astra.client.collections.Collection;
import com.datastax.astra.client.collections.commands.cursor.CollectionFindCursor;
import com.datastax.astra.client.collections.definition.documents.Document;
import com.datastax.astra.client.core.query.Filter;
import com.datastax.astra.client.core.query.Filters;
import com.datastax.astra.client.core.options.DataAPIClientOptions;
import com.datastax.astra.client.databases.Database;
import com.datastax.astra.client.databases.DatabaseOptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

@Service
public class AstraQuestionService {

    private final DataAPIClient client;
    private final Database database;
    private final Collection<Document> collection;

    public AstraQuestionService(@Value("${astra.db.url}") String dbUrl,
                                @Value("${astra.db.keyspace}") String keyspace,
                                @Value("${astra.db.token}") String token) {
        this.client = new DataAPIClient(new DataAPIClientOptions());
        DatabaseOptions options = new DatabaseOptions().keyspace(keyspace).token(token);
        this.database = client.getDatabase(
                dbUrl,
                options);

        this.collection = database.getCollection("question");

        // Check if collection exists (Optional)
        if (this.collection == null) {
            throw new IllegalStateException("Collection 'question' does not exist in the Astra Database.");
        }
        }

    public List<Document> getAllQuestions() {
        return collection.find((Filter) null).toList();
    }

    public Document getRandomQuestion() {
        List<Document> allQuestions = getAllQuestions();
        if (allQuestions.isEmpty()) return null;
        int index = (int) (Math.random() * allQuestions.size());
        return allQuestions.get(index);
    }

    public List<Document> getRandomFiveQuestions(String question_type) {
    CollectionFindCursor<Document, Document> cursor = collection.find(Filters.eq("question_type", "python"));

    List<Document> allQuestions = new ArrayList<>();
    cursor.forEach(allQuestions::add);

    Collections.shuffle(allQuestions);
    return allQuestions.size() <= 5 ? allQuestions : allQuestions.subList(0, 5);
}

public List<Document>  getQuestionById(String questionId) {
        try {
            CollectionFindCursor<Document, Document>  document = collection.find(Filters.eq("_id", questionId));
            List<Document> allQuestions = new ArrayList<>();
            document.forEach(allQuestions::add);
            System.out.println("document : "+ allQuestions);
            return allQuestions;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
