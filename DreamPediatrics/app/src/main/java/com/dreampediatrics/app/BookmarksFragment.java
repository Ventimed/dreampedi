package com.dreampediatrics.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.util.List;

public class BookmarksFragment extends Fragment {
    private LinearLayout bookmarksContainer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookmarks, container, false);
        bookmarksContainer = view.findViewById(R.id.bookmarksContainer);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateBookmarks();
    }

    private void populateBookmarks() {
        bookmarksContainer.removeAllViews();
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        List<BookmarkItem> bookmarkItems = mainActivity.getBookmarks();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (bookmarkItems.isEmpty()) {
            View emptyView = inflater.inflate(R.layout.empty_state, bookmarksContainer, false);
            TextView emptyTitle = emptyView.findViewById(R.id.emptyTitle);
            TextView emptyMessage = emptyView.findViewById(R.id.emptyMessage);
            ImageView Img = emptyView.findViewById(R.id.fragicon);
            Img.setImageResource(R.drawable.ic_bookmark_solid);
            emptyTitle.setText("No Bookmarks");
            emptyMessage.setText("Bookmark important sections for quick access");
            bookmarksContainer.addView(emptyView);
            return;
        }

        for (BookmarkItem item : bookmarkItems) {
            View bookmarkView = inflater.inflate(R.layout.item_bookmark, bookmarksContainer, false);

            TextView titleView = bookmarkView.findViewById(R.id.bookmarkTitle);
            TextView subtitleView = bookmarkView.findViewById(R.id.bookmarkSubtitle);
            TextView infoView = bookmarkView.findViewById(R.id.bookmarkInfo);
            CardView cardView = (CardView) bookmarkView;

            titleView.setText(item.getTitle());
            subtitleView.setText(item.getSnippet());

            cardView.setOnClickListener(v -> {
                // Open the topic by topicId or rowid stored in the BookmarkItem
                mainActivity.openTopic(item.getTopicId());
            });

            // Touch effect
            cardView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                        break;
                }
                return false;
            });

            bookmarksContainer.addView(bookmarkView);
        }
    }
}
