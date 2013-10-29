/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.service.IssueService;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.androidquery.AQuery;
import com.gh4a.Constants;
import com.gh4a.Gh4Application;
import com.gh4a.ProgressDialogTask;
import com.gh4a.R;
import com.gh4a.adapter.CommentAdapter;
import com.gh4a.loader.IsCollaboratorLoader;
import com.gh4a.loader.IssueCommentListLoader;
import com.gh4a.loader.IssueLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.utils.GravatarUtils;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.ToastUtils;
import com.gh4a.utils.UiUtils;
import com.github.mobile.util.HtmlUtils;
import com.github.mobile.util.HttpImageGetter;

public class IssueActivity extends BaseSherlockFragmentActivity implements
        OnClickListener, CommentAdapter.OnEditComment {

    private static final int REQUEST_EDIT = 1000;

    private Issue mIssue;
    private String mRepoOwner;
    private String mRepoName;
    private int mIssueNumber;
    private String mIssueState;
    private CommentAdapter mCommentAdapter;
    private boolean isCollaborator;
    private boolean isCreator;
    private ProgressDialog mProgressDialog;
    private AQuery aq;

    private LoaderCallbacks<Issue> mIssueCallback = new LoaderCallbacks<Issue>() {
        @Override
        public Loader<LoaderResult<Issue>> onCreateLoader(int id, Bundle args) {
            return new IssueLoader(IssueActivity.this, mRepoOwner, mRepoName, mIssueNumber);
        }
        @Override
        public void onResultReady(LoaderResult<Issue> result) {
            hideLoading();
            if (!result.handleError(IssueActivity.this)) {
                mIssue = result.getData();
                mIssueState = mIssue.getState();
                fillData();
                getSupportLoaderManager().initLoader(1, null, mCollaboratorCallback);
                getSupportLoaderManager().initLoader(2, null, mCommentCallback);
            }
            else {
                invalidateOptionsMenu();
            }
        }
    };

    private LoaderCallbacks<List<Comment>> mCommentCallback = new LoaderCallbacks<List<Comment>>() {
        @Override
        public Loader<LoaderResult<List<Comment>>> onCreateLoader(int id, Bundle args) {
            return new IssueCommentListLoader(IssueActivity.this, mRepoOwner, mRepoName, mIssueNumber);
        }
        @Override
        public void onResultReady(LoaderResult<List<Comment>> result) {
            if (!result.handleError(IssueActivity.this)) {
                fillComments(result.getData());
            }
        }
    };

    private LoaderCallbacks<Boolean> mCollaboratorCallback = new LoaderCallbacks<Boolean>() {
        @Override
        public Loader<LoaderResult<Boolean>> onCreateLoader(int id, Bundle args) {
            return new IsCollaboratorLoader(IssueActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<Boolean> result) {
            if (!result.handleError(IssueActivity.this)) {
                isCollaborator = result.getData();
                isCreator = mIssue.getUser().getLogin().equals(
                        Gh4Application.get(IssueActivity.this).getAuthLogin());
                invalidateOptionsMenu();
            }
        }
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(Gh4Application.THEME);
        super.onCreate(savedInstanceState);

        Bundle data = getIntent().getExtras();
        mRepoOwner = data.getString(Constants.Repository.REPO_OWNER);
        mRepoName = data.getString(Constants.Repository.REPO_NAME);
        mIssueNumber = data.getInt(Constants.Issue.ISSUE_NUMBER);
        mIssueState = data.getString(Constants.Issue.ISSUE_STATE);
        
        if (!isOnline()) {
            setErrorView();
            return;
        }
        
        setContentView(R.layout.issue);

        aq = new AQuery(this);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getResources().getString(R.string.issue) + " #" + mIssueNumber);
        actionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        RelativeLayout tlComment = (RelativeLayout) findViewById(R.id.rl_comment);
        if (!Gh4Application.get(this).isAuthorized()) {
            tlComment.setVisibility(View.GONE);
        }
        
        getSupportLoaderManager().initLoader(0, null, mIssueCallback);
    }

    private void fillData() {
        final Gh4Application app = Gh4Application.get(this);
        
        ListView lvComments = (ListView) findViewById(R.id.list_view);
        // set details inside listview header
        LayoutInflater infalter = getLayoutInflater();
        LinearLayout mHeader = (LinearLayout) infalter.inflate(R.layout.issue_header, lvComments, false);
        mHeader.setClickable(false);
        
        lvComments.addHeaderView(mHeader, null, false);
        
        RelativeLayout rlComment = (RelativeLayout) findViewById(R.id.rl_comment);
        if (!Gh4Application.get(this).isAuthorized()) {
            rlComment.setVisibility(View.GONE);
        }

        TextView tvCommentTitle = (TextView) mHeader.findViewById(R.id.comment_title);
        mCommentAdapter = new CommentAdapter(this, mRepoOwner, this);
        lvComments.setAdapter(mCommentAdapter);

        ImageView ivGravatar = (ImageView) mHeader.findViewById(R.id.iv_gravatar);
        aq.id(R.id.iv_gravatar).image(GravatarUtils.getGravatarUrl(mIssue.getUser().getGravatarId()),
                true, false, 0, 0, aq.getCachedImage(R.drawable.default_avatar), AQuery.FADE_IN);
        
        ivGravatar.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                app.openUserInfoActivity(IssueActivity.this,
                        mIssue.getUser().getLogin(), null);
            }
        });

        TextView tvExtra = (TextView) mHeader.findViewById(R.id.tv_extra);
        TextView tvState = (TextView) mHeader.findViewById(R.id.tv_state);
        TextView tvTitle = (TextView) mHeader.findViewById(R.id.tv_title);
        TextView tvDescTitle = (TextView) mHeader.findViewById(R.id.desc_title);
        tvDescTitle.setTypeface(app.boldCondensed);
        tvDescTitle.setTextColor(getResources().getColor(R.color.highlight));
        
        tvCommentTitle.setTypeface(app.boldCondensed);
        tvCommentTitle.setTextColor(getResources().getColor(R.color.highlight));
        tvCommentTitle.setText(getResources().getString(R.string.issue_comments) + " (" + mIssue.getComments() + ")");
        
        TextView tvDesc = (TextView) mHeader.findViewById(R.id.tv_desc);
        tvDesc.setMovementMethod(LinkMovementMethod.getInstance());
        
        TextView tvMilestone = (TextView) mHeader.findViewById(R.id.tv_milestone);
        
        ImageView ivComment = (ImageView) findViewById(R.id.iv_comment);
        if (Gh4Application.THEME == R.style.DefaultTheme) {
            ivComment.setImageResource(R.drawable.social_send_now_dark);
        }
        ivComment.setBackgroundResource(R.drawable.abs__list_selector_holo_dark);
        ivComment.setPadding(5, 2, 5, 2);
        ivComment.setOnClickListener(this);

        tvExtra.setText(mIssue.getUser().getLogin() + "\n" + Gh4Application.pt.format(mIssue.getCreatedAt()));
        tvState.setTextColor(Color.WHITE);
        if ("closed".equals(mIssue.getState())) {
            tvState.setBackgroundResource(R.drawable.default_red_box);
            tvState.setText(getString(R.string.closed).toUpperCase(Locale.getDefault()));
        }
        else {
            tvState.setBackgroundResource(R.drawable.default_green_box);
            tvState.setText(getString(R.string.open).toUpperCase(Locale.getDefault()));
        }
        tvTitle.setText(mIssue.getTitle());
        tvTitle.setTypeface(app.boldCondensed);
        
        boolean showInfoBox = false;
        if (mIssue.getAssignee() != null) {
            showInfoBox = true;
            TextView tvAssignee = (TextView) mHeader.findViewById(R.id.tv_assignee);
            tvAssignee.setText(getString(R.string.issue_assignee, mIssue.getAssignee().getLogin()));
            tvAssignee.setVisibility(View.VISIBLE);
            tvAssignee.setOnClickListener(new OnClickListener() {
                
                @Override
                public void onClick(View arg0) {
                    app.openUserInfoActivity(IssueActivity.this,
                            mIssue.getAssignee().getLogin(), null);
                }
            });
            
            ImageView ivAssignee = (ImageView) mHeader.findViewById(R.id.iv_assignee);
            
            aq.id(R.id.iv_assignee).image(GravatarUtils.getGravatarUrl(mIssue.getAssignee().getGravatarId()),
                    true, false, 0, 0, aq.getCachedImage(R.drawable.default_avatar), AQuery.FADE_IN);
            
            ivAssignee.setVisibility(View.VISIBLE);
            ivAssignee.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    Gh4Application.get(IssueActivity.this).openUserInfoActivity(IssueActivity.this,
                            mIssue.getAssignee().getLogin(), null);
                }
            });
        }
        
        if (mIssue.getMilestone() != null) {
            showInfoBox = true;
            tvMilestone.setText(getString(R.string.issue_milestone, mIssue.getMilestone().getTitle()));
        }
        else {
            tvMilestone.setVisibility(View.GONE);
        }
        
        String body = mIssue.getBodyHtml();
        if (!StringUtils.isBlank(body)) {
            HttpImageGetter imageGetter = new HttpImageGetter(this);
            body = HtmlUtils.format(body).toString();
            imageGetter.bind(tvDesc, body, mIssue.getNumber());
        }
        
        LinearLayout llLabels = (LinearLayout) findViewById(R.id.ll_labels);
        List<Label> labels = mIssue.getLabels();
        
        if (labels != null && !labels.isEmpty()) {
            showInfoBox = true;
            for (Label label : labels) {
                TextView tvLabel = new TextView(this);
                tvLabel.setSingleLine(true);
                tvLabel.setText(label.getName());
                tvLabel.setTextAppearance(this, R.style.default_text_small);
                tvLabel.setBackgroundColor(Color.parseColor("#" + label.getColor()));
                tvLabel.setPadding(5, 2, 5, 2);
                int r = Color.red(Color.parseColor("#" + label.getColor()));
                int g = Color.green(Color.parseColor("#" + label.getColor()));
                int b = Color.blue(Color.parseColor("#" + label.getColor()));
                if (r + g + b < 383) {
                    tvLabel.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
                }
                else {
                    tvLabel.setTextColor(getResources().getColor(android.R.color.primary_text_light));
                }
                llLabels.addView(tvLabel);
                
                View v = new View(this);
                v.setLayoutParams(new LayoutParams(5, LayoutParams.WRAP_CONTENT));
                llLabels.addView(v);
            }
        }
        else {
            llLabels.setVisibility(View.GONE);
        }
        
        TextView tvPull = (TextView) mHeader.findViewById(R.id.tv_pull);
        if (mIssue.getPullRequest() != null
                && mIssue.getPullRequest().getDiffUrl() != null) {
            showInfoBox = true;
            tvPull.setVisibility(View.VISIBLE);
            tvPull.setOnClickListener(this);
        }
        
        if (!showInfoBox) {
            RelativeLayout rl = (RelativeLayout) mHeader.findViewById(R.id.info_box);
            rl.setVisibility(View.GONE);
        }
    }

    protected void fillComments(List<Comment> comments) {
        stopProgressDialog(mProgressDialog);
        mCommentAdapter.clear();
        if (comments != null && comments.size() > 0) {
            mCommentAdapter.notifyDataSetChanged();
            for (Comment comment : comments) {
                mCommentAdapter.add(comment);
            }
        }
        mCommentAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (Gh4Application.get(this).isAuthorized()) {
            menu.clear();
            MenuInflater inflater = getSupportMenuInflater();
            inflater.inflate(R.menu.issue_menu, menu);

            if ("closed".equals(mIssueState)) {
                menu.removeItem(R.id.issue_close);
            }
            else {
                menu.removeItem(R.id.issue_reopen);
            }
            
            if (!isCollaborator && !isCreator) {
                menu.removeItem(R.id.issue_close);
                menu.removeItem(R.id.issue_reopen);
                menu.removeItem(R.id.issue_edit);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void navigateUp() {
        Gh4Application.get(this).openIssueListActivity(this, mRepoOwner, mRepoName,
                Constants.Issue.ISSUE_STATE_OPEN, Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.issue_create:
                if (checkForAuthOrExit()) {
                    Intent intent = new Intent().setClass(this, IssueCreateActivity.class);
                    intent.putExtra(Constants.Repository.REPO_OWNER, mRepoOwner);
                    intent.putExtra(Constants.Repository.REPO_NAME, mRepoName);
                    startActivity(intent);
                }
                return true;
            case R.id.issue_edit:
                if (checkForAuthOrExit()) {
                    Intent intent = new Intent().setClass(this, IssueCreateActivity.class);
                    intent.putExtra(Constants.Repository.REPO_OWNER, mRepoOwner);
                    intent.putExtra(Constants.Repository.REPO_NAME, mRepoName);
                    intent.putExtra(Constants.Issue.ISSUE_NUMBER, mIssue.getNumber());
                    startActivity(intent);
                }
                return true;
            case R.id.issue_close:
            case R.id.issue_reopen:
                if (checkForAuthOrExit()) {
                    new IssueOpenCloseTask(item.getItemId() == R.id.issue_reopen).execute();
                }
                return true;
            case R.id.share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_issue_subject,
                        mIssueNumber, mIssue.getTitle(), mRepoOwner + "/" + mRepoName));
                shareIntent.putExtra(Intent.EXTRA_TEXT,  mIssue.getHtmlUrl());
                shareIntent = Intent.createChooser(shareIntent, getString(R.string.share_title));
                startActivity(shareIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private boolean checkForAuthOrExit() {
        if (Gh4Application.get(this).isAuthorized()) {
            return true;
        }
        Intent intent = new Intent().setClass(this, Github4AndroidActivity.class);
        startActivity(intent);
        finish();
        return false;
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.iv_comment:
            EditText etComment = (EditText) findViewById(R.id.et_comment);
            String comment = etComment.getText() == null ? null : etComment.getText().toString();
            if (!StringUtils.isBlank(comment)) {
                new CommentIssueTask(comment).execute();
            }
            UiUtils.hideImeForView(getCurrentFocus());
            break;
        case R.id.tv_pull:
            Gh4Application.get(this).openPullRequestActivity(this,
                    mRepoOwner, mRepoName, mIssueNumber);
            break;
        default:
            break;
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT && resultCode == Activity.RESULT_OK) {
            getSupportLoaderManager().restartLoader(2, null, mCommentCallback);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void editComment(Comment comment) {
        Intent intent = new Intent(this, EditCommentActivity.class);
        
        intent.putExtra(Constants.Repository.REPO_OWNER, mRepoOwner);
        intent.putExtra(Constants.Repository.REPO_NAME, mRepoName);
        intent.putExtra(Constants.Comment.ID, comment.getId());
        intent.putExtra(Constants.Comment.BODY, comment.getBody());
        startActivityForResult(intent, REQUEST_EDIT);
    }

    private class IssueOpenCloseTask extends ProgressDialogTask<Issue> {
        private boolean mOpen;
        
        public IssueOpenCloseTask(boolean open) {
            super(IssueActivity.this, 0, open ? R.string.opening_msg : R.string.closing_msg);
            mOpen = open;
        }

        @Override
        protected Issue run() throws IOException {
            IssueService issueService = (IssueService)
                    Gh4Application.get(mContext).getService(Gh4Application.ISSUE_SERVICE);
            RepositoryId repoId = new RepositoryId(mRepoOwner, mRepoName);
            
            Issue issue = issueService.getIssue(repoId, mIssueNumber);
            issue.setState(mOpen ? "open" : "closed");

            return issueService.editIssue(repoId, issue);
        }

        @Override
        protected void onSuccess(Issue result) {
            mIssue = result;
            mIssueState = mOpen ? "open" : "closed";
            ToastUtils.showMessage(mContext,
                    mOpen ? R.string.issue_success_reopen : R.string.issue_success_close);
            
            TextView tvState = (TextView) findViewById(R.id.tv_state);
            tvState.setBackgroundResource(mOpen ? R.drawable.default_green_box : R.drawable.default_red_box);
            tvState.setText(getString(mOpen ? R.string.open : R.string.closed).toUpperCase(Locale.getDefault()));
            invalidateOptionsMenu();
        }
        
        @Override
        protected void onError(Exception e) {
            ToastUtils.showMessage(mContext, R.string.issue_error_close);
        }
    }
    
    private class CommentIssueTask extends ProgressDialogTask<Void> {
        private String mComment;

        public CommentIssueTask(String comment) {
            super(IssueActivity.this, 0, R.string.loading_msg);
            mComment = comment;
        }

        @Override
        protected Void run() throws IOException {
            IssueService issueService = (IssueService)
                    Gh4Application.get(mContext).getService(Gh4Application.ISSUE_SERVICE);
            issueService.createComment(mRepoOwner, mRepoName, mIssueNumber, mComment);
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            ToastUtils.showMessage(mContext, R.string.issue_success_comment);
            //reload comments
            getSupportLoaderManager().restartLoader(2, null, mCommentCallback);
            
            EditText etComment = (EditText) findViewById(R.id.et_comment);
            etComment.setText(null);
            etComment.clearFocus();
        }
        
        @Override
        protected void onError(Exception e) {
            ToastUtils.showMessage(mContext, R.string.issue_error_comment);
        }
    }
}