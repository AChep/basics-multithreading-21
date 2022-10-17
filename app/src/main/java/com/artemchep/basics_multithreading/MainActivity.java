package com.artemchep.basics_multithreading;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artemchep.basics_multithreading.cipher.CipherUtil;
import com.artemchep.basics_multithreading.domain.Message;
import com.artemchep.basics_multithreading.domain.WithMillis;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private List<WithMillis<Message>> mList = new ArrayList<>();

    private MessageAdapter mAdapter = new MessageAdapter(mList);

    private final Agent mAgent = new Agent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAgent.start();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);

        showWelcomeDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAgent.interrupt();
    }

    private void showWelcomeDialog() {
        new AlertDialog.Builder(this)
                .setMessage("What are you going to need for this task: Thread, Handler.\n" +
                        "\n" +
                        "1. The main thread should never be blocked.\n" +
                        "2. Messages should be processed sequentially.\n" +
                        "3. The elapsed time SHOULD include the time message spent in the queue.")
                .show();
    }

    public void onPushBtnClick(View view) {
        Message message = Message.generate();
        insert(new WithMillis<>(message));
    }

    @UiThread
    public void insert(final WithMillis<Message> message) {
        mList.add(message);
        mAdapter.notifyItemInserted(mList.size() - 1);

        final long time = SystemClock.elapsedRealtime();
        mAgent.submit(new Runnable() {
            @Override
            public void run() {
                final String cipherText = CipherUtil.encrypt(message.value.plainText);
                final Message cipherMessage = message.value.copy(cipherText);

                final long elapsedTime = SystemClock.elapsedRealtime() - time;
                final WithMillis<Message> item = new WithMillis<>(cipherMessage, elapsedTime);
                updateOnUiThread(item);
            }
        });
    }

    @WorkerThread
    public void updateOnUiThread(final WithMillis<Message> message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update(message);
            }
        });
    }

    @UiThread
    public void update(final WithMillis<Message> message) {
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).value.key.equals(message.value.key)) {
                mList.set(i, message);
                mAdapter.notifyItemChanged(i);
                return;
            }
        }

        throw new IllegalStateException();
    }

    private static class Agent extends Thread {
        private final Queue<Runnable> queue = new LinkedList<>();

        @Override
        public void run() {
            super.run();
            while (!interrupted()) {
                final Runnable task;
                try {
                    task = poll();
                } catch (InterruptedException e) {
                    break;
                }

                task.run();
            }
        }

        private synchronized Runnable poll() throws InterruptedException {
            while (true) {
                final Runnable runnable = queue.poll();
                if (runnable != null) {
                    return runnable;
                }

                // Wait till someone puts an item
                // into the queue and wakes us up.
                wait();
            }
        }

        public synchronized void submit(Runnable runnable) {
            queue.add(runnable);
            notifyAll();
        }
    }
}
