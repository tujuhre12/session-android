package org.thoughtcrime.securesms.reactions.any;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import network.loki.messenger.R;

public final class ReactWithAnyEmojiDialogFragment extends BottomSheetDialogFragment
{

  private static final String ARG_MESSAGE_ID = "arg_message_id";
  private static final String ARG_IS_MMS     = "arg_is_mms";
  private static final String ARG_START_PAGE = "arg_start_page";
  private static final String ARG_SHADOWS    = "arg_shadows";

  private Callback                   callback;

  public static DialogFragment createForMessageRecord(@NonNull MessageRecord messageRecord, int startingPage) {
    DialogFragment fragment = new ReactWithAnyEmojiDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, messageRecord.getId());
    args.putBoolean(ARG_IS_MMS, messageRecord.isMms());
    args.putInt(ARG_START_PAGE, startingPage);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (getParentFragment() instanceof Callback) {
      callback = (Callback) getParentFragment();
    } else {
      callback = (Callback) context;
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
    BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.Theme_Session_BottomSheet);
    dialog.getBehavior().setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.8));
   // dialog.getBehavior().setDraggable(false);

    boolean shadows = requireArguments().getBoolean(ARG_SHADOWS, true);
    if (!shadows) {
      Window window = dialog.getWindow();
      if (window != null) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      }
    }

    return dialog;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.react_with_any_emoji_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModel();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LoaderManager.getInstance(requireActivity()).destroyLoader((int) requireArguments().getLong(ARG_MESSAGE_ID));
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);

    callback.onReactWithAnyEmojiDialogDismissed();
  }

  private void initializeViewModel() {
    Bundle                             args       = requireArguments();
    ReactWithAnyEmojiRepository        repository = new ReactWithAnyEmojiRepository(requireContext());
  }

  public void onEmojiSelected(String emoji) {
    Bundle    args      = requireArguments();
    MessageId messageId = new MessageId(args.getLong(ARG_MESSAGE_ID), args.getBoolean(ARG_IS_MMS));
    callback.onReactWithAnyEmojiSelected(emoji, messageId);
    dismiss();
  }

  public interface Callback {
    void onReactWithAnyEmojiDialogDismissed();

    void onReactWithAnyEmojiSelected(@NonNull String emoji, MessageId messageId);
  }

}
