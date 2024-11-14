/*
 * Copyright 2023-2024 kachaya
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.toribane.kkbd;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class LearningDictionaryTool extends AppCompatActivity {
    public Dictionary mDictionary;
    ActivityResultLauncher<Intent> exportResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::onExportActivityResult);
    private ArrayAdapter<String> mAdapter;
    ActivityResultLauncher<Intent> importResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::onImportActivityResult);

    private void showToast(String str) {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
        toast.show();
    }

    AdapterView.OnItemClickListener itemClickListener = (parent, view, position, l) -> {
        String item = (String) parent.getItemAtPosition(position);
        String[] ss = item.split("\t", 2);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("読み:" + ss[0]);
        builder.setMessage("「" + ss[1] + "」を削除しますか");
        builder.setPositiveButton("はい", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int idx) {
                showToast("削除しました");
                mDictionary.deleteLearning(ss[0]);
                mAdapter.clear();
                mAdapter.addAll(exportDictionary());
                mAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int idx) {
                showToast("キャンセルしました");
            }
        });
        builder.create();
        builder.show();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.learning_dictionary_tool);
        mDictionary = new Dictionary(this);

        Button exportButton = findViewById(R.id.export_button);
        exportButton.setOnClickListener(this::onClickExportDictionary);
        Button importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(this::onClickImportDictionary);

        ListView listView = findViewById(R.id.list_view);
        listView.setOnItemClickListener(itemClickListener);
        listView.setEmptyView(findViewById(R.id.empty_text));

        ArrayList<String> dataList = new ArrayList<>();

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(mAdapter);

        mAdapter.clear();
        mAdapter.addAll(exportDictionary());
        mAdapter.notifyDataSetChanged();
    }

    public String getDefaultFileName() {
        return mDictionary.getLearningDictionaryName() + ".txt";
    }

    public void importDictionary(ArrayList<String> entries) {
        mDictionary.importLearningDictionary(entries);
    }

    public ArrayList<String> exportDictionary() {
        return mDictionary.exportLearningDictionary();
    }

    private void onClickExportDictionary(View v) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultFileName());
        exportResultLauncher.launch(intent);
    }

    private void onClickImportDictionary(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDefaultFileName());
        importResultLauncher.launch(intent);
    }

    private void onImportActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent resultData = result.getData();
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream)));
                    String entry;
                    ArrayList<String> entries = new ArrayList<>();
                    while ((entry = reader.readLine()) != null) {
                        entries.add(entry);
                    }
                    reader.close();
                    importDictionary(entries);
                    mAdapter.clear();
                    mAdapter.addAll(exportDictionary());
                    mAdapter.notifyDataSetChanged();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void onExportActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent resultData = result.getData();
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    BufferedWriter writer = new BufferedWriter((new OutputStreamWriter(outputStream)));
                    for (String entry : exportDictionary()) {
                        writer.write(entry + "\n");
                    }
                    writer.flush();
                    writer.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
