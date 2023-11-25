// Generated by view binder compiler. Do not edit!
package com.example.platform.ui.windowmanager.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.example.platform.ui.windowmanager.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ActivitySplitActivityListDetailLayoutBinding implements ViewBinding {
  @NonNull
  private final RelativeLayout rootView;

  @NonNull
  public final Button infoButton;

  @NonNull
  public final TextView itemDetailText;

  @NonNull
  public final RelativeLayout rootSplitActivityLayout;

  private ActivitySplitActivityListDetailLayoutBinding(@NonNull RelativeLayout rootView,
      @NonNull Button infoButton, @NonNull TextView itemDetailText,
      @NonNull RelativeLayout rootSplitActivityLayout) {
    this.rootView = rootView;
    this.infoButton = infoButton;
    this.itemDetailText = itemDetailText;
    this.rootSplitActivityLayout = rootSplitActivityLayout;
  }

  @Override
  @NonNull
  public RelativeLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivitySplitActivityListDetailLayoutBinding inflate(
      @NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivitySplitActivityListDetailLayoutBinding inflate(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_split_activity_list_detail_layout, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivitySplitActivityListDetailLayoutBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.infoButton;
      Button infoButton = ViewBindings.findChildViewById(rootView, id);
      if (infoButton == null) {
        break missingId;
      }

      id = R.id.item_detail_text;
      TextView itemDetailText = ViewBindings.findChildViewById(rootView, id);
      if (itemDetailText == null) {
        break missingId;
      }

      RelativeLayout rootSplitActivityLayout = (RelativeLayout) rootView;

      return new ActivitySplitActivityListDetailLayoutBinding((RelativeLayout) rootView, infoButton,
          itemDetailText, rootSplitActivityLayout);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}