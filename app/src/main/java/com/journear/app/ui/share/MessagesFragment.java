package com.journear.app.ui.share;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.journear.app.R;
import com.journear.app.core.LocalFunctions;
import com.journear.app.core.entities.NearbyDevice;
import com.journear.app.core.entities.UserSkimmed;
import com.journear.app.core.services.CommunicationListener;
import com.journear.app.core.services.JnMessage;
import com.journear.app.core.services.JnMessageSet;
import com.journear.app.core.services.ServiceLocator;
import com.journear.app.ui.MainActivity;

import org.apache.commons.lang3.StringUtils;

public class MessagesFragment extends Fragment implements CommunicationListener {

    private static final String LOGTAG = "ShareFragmentLogs";
    private MessagesViewModel messagesViewModel;
    private TextView requestTextView;
    private Button acceptButton;
    private Button rejectButton;
    private View layoutToAdd;
    private LinearLayout container;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        messagesViewModel =
                ViewModelProviders.of(this).get(MessagesViewModel.class);
        View root = inflater.inflate(R.layout.fragment_share, container, false);
        return root;
    }

    MainActivity mainActivity;

    @Override
    public void onStart() {
        super.onStart();
        mainActivity = (MainActivity) getActivity();
        for (MainActivity.CachedComms cachedComms : mainActivity.getCachedCommsList()) {
            if (cachedComms.isExpired()) {
                onExpire(cachedComms.getMessage(), cachedComms.getAssociatedRide());
            } else {
                onResponse(cachedComms.getMessage(), cachedComms.getAssociatedRide());
            }
        }
    }

    private void Reject(JnMessage message, NearbyDevice associatedRide, View v) {
        ServiceLocator.getCommunicationHub().sendMessage(associatedRide, JnMessageSet.Reject, this);
        hideButtonsOnLayout(v);
    }

    public void Accept(JnMessage message, NearbyDevice nd, View v) {
        try {
            UserSkimmed userSkimmed = new UserSkimmed();
            userSkimmed.name = message.getSenderName();
            userSkimmed.gender = message.getSenderGender();
            userSkimmed.setUserId(message.getSender());

            nd.getTravellers().add(userSkimmed);
            LocalFunctions.setCurrentJourney(nd);
            try {
                mainActivity.devicesList.remove(mainActivity.ndOwnJourneyPlan);
                mainActivity.devicesList.add(nd);
            } catch (Exception ex) {
                Log.e(LOGTAG, "Could not replace the nearby device in deviceslist on Accepting.", ex);
            }
            mainActivity.ndOwnJourneyPlan = nd;
            ServiceLocator.getCommunicationHub().sendMessage(nd, JnMessageSet.Accept, this);
            hideButtonsOnLayout(v);
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in sending Accept", ex);

        }
    }

    private void hideButtonsOnLayout(View v) {
        try {
            if (v != null && v.getParent() instanceof LinearLayout) {
                ((LinearLayout) v.getParent()).setVisibility(View.INVISIBLE);
            }
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in hiding buttons", ex);
        }
    }

    @Override
    public void onResponse(final JnMessage message, final NearbyDevice associatedRide) {
        try {
            container = getView().findViewById(R.id.messages_container);

            if (message.getMessageFlag() == JnMessageSet.RequestToJoin) {

                layoutToAdd = getLayoutInflater().inflate(R.layout.accept_reject_button_layout, container, false);
                acceptButton = layoutToAdd.findViewById(R.id.message_accept);

                requestTextView = layoutToAdd.findViewById(R.id.request_text);
                String request_message_text = "User : " + message.getSenderName() + " is requesting to join your ride from " + associatedRide.getSource2().placeString + " to " + associatedRide.getDestination2().toString() + ".";
                requestTextView.setText(request_message_text);

                acceptButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Accept(message, associatedRide, v);
                    }
                });

                rejectButton = layoutToAdd.findViewById(R.id.message_reject);

                rejectButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Reject(message, associatedRide, v);

                    }
                });
                container.addView(layoutToAdd);
            } else {
                // fill the tv
                TextView tv = new TextView(getContext());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, // Width of TextView
                        LinearLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

                tv.setLayoutParams(lp);
                tv.setText(messageResponseJourney(associatedRide, message));
                container.addView(tv);

                if (message.getMessageFlag() == JnMessageSet.Accept) {
                    UserSkimmed userSkimmed = (UserSkimmed) LocalFunctions.getCurrentUser();
                    associatedRide.getTravellers().add(userSkimmed);
                    LocalFunctions.setCurrentJourney(associatedRide);
                    try {
                        mainActivity.devicesList.remove(mainActivity.ndOwnJourneyPlan);
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "Couldn't update the devicelist on Accept", ex);
                    }
                    mainActivity.ndOwnJourneyPlan = associatedRide;
                }
            }
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in onResponse", ex);

        }
    }

    private String messageResponseJourney(NearbyDevice associatedRide, JnMessage message) {
        String past = "";
        if (message.getMessageFlag() == JnMessageSet.Okay) {
            past = "Okay";
        } else {
            past = message.getMessageFlag().name() + "ed";
        }

        String contactInfo = message.getMessageFlag() == JnMessageSet.Accept ? "Contact Info:" + message.getPhoneNumber() : " ";
        String messageResponse = past + " :Your Journey from " + associatedRide.getSource2().placeString +
                " to " + associatedRide.getDestination2().placeString + " at " + associatedRide.getTravelTime() +
                " has been " + StringUtils.lowerCase(past) + " by " + associatedRide.getOwner().getName() + ". " + contactInfo;

        return messageResponse;
    }


    @Override
    public void onExpire(JnMessage expiredMessage, NearbyDevice nearbyDevice) {
        try {
            // Add an entry to the linear layout with "Expired: " before the text
            container = getView().findViewById(R.id.messages_container);
            layoutToAdd = getLayoutInflater().inflate(R.layout.accept_reject_button_layout, container, false);
            acceptButton = layoutToAdd.findViewById(R.id.message_accept);
            rejectButton = layoutToAdd.findViewById(R.id.message_reject);
            layoutToAdd.setBackgroundColor(getResources().getColor(R.color.disabled));
            acceptButton.setEnabled(false);
            rejectButton.setEnabled(false);
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in onExpire", ex);
        }
    }
}