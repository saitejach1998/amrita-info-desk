/*
 * MIT License
 *
 * Copyright (c) 2016 Niranjan Rajendran
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.njlabs.amrita.aid.aums;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.njlabs.amrita.aid.BaseActivity;
import com.njlabs.amrita.aid.R;
import com.njlabs.amrita.aid.aums.client.Aums;
import com.njlabs.amrita.aid.aums.models.CourseData;
import com.njlabs.amrita.aid.aums.models.CourseResource;
import com.njlabs.amrita.aid.aums.responses.CourseResourcesResponse;
import com.njlabs.amrita.aid.aums.responses.CoursesResponse;
import com.njlabs.amrita.aid.util.ExtendedSwipeRefreshLayout;
import com.njlabs.amrita.aid.util.okhttp.ProgressResponseBody;
import com.njlabs.amrita.aid.util.okhttp.responses.FileResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class AumsResourcesActivity extends BaseActivity {

    List<CourseData> courseDatas;
    private ExtendedSwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private Aums aums;
    private String courseId = null;
    private String fileNameToDownload = null;

    private ProgressDialog progressDialog;

    public static String humanReadableByteCount(long bytes) {
        return humanReadableByteCount(bytes, false);
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public void init(Bundle savedInstanceState) {

        setupLayout(R.layout.activity_aums_list, Color.parseColor("#e91e63"));
        String server = getIntent().getStringExtra("server");

        swipeRefreshLayout = (ExtendedSwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        recyclerView = (RecyclerView) findViewById(R.id.list);

        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#e91e63"));
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reloadList();
            }
        });
        final LinearLayoutManager layoutParams = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutParams);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        swipeRefreshLayout.setRefreshing(true);

        progressDialog = new ProgressDialog(baseContext);
        progressDialog.setTitle("Downloading ... ");
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                fileNameToDownload = null;
            }
        });

        aums = new Aums(baseContext, new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(final long bytesRead, final long contentLength, boolean done) {
                final int progress = (int) ((bytesRead / contentLength) * 100);
                ((Activity) baseContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setMessage(humanReadableByteCount(bytesRead) + " of " + humanReadableByteCount(contentLength));
                        progressDialog.setProgress(progress);
                    }
                });
            }
        });

        aums.setServer(server);
        reloadList();
    }

    private void reloadList() {
        if (courseId == null) {
            aums.getCourses(new CoursesResponse() {
                @Override
                public void onSuccess(List<CourseData> courseDataList) {
                    swipeRefreshLayout.setRefreshing(false);
                    courseDatas = courseDataList;
                    CoursesListAdapter adapter = new CoursesListAdapter(courseDataList);
                    recyclerView.setAdapter(adapter);
                }

                @Override
                public void onSiteStructureChange() {
                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        } else {
            aums.getCourseResources(courseId, new CourseResourcesResponse() {
                @Override
                public void onSuccess(List<CourseResource> courseResourceList) {

                    Collections.sort(courseResourceList, new Comparator<CourseResource>() {
                        public int compare(CourseResource courseResource1, CourseResource courseResource2) {
                            return courseResource1.getResourceFileName().compareToIgnoreCase(courseResource2.getResourceFileName());
                        }
                    });

                    CourseResourcesAdapter adapter = new CourseResourcesAdapter(courseResourceList);

                    recyclerView.setAdapter(adapter);
                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onSiteStructureChange() {
                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
    }

    public void itemClick(View v) {
        if (courseId == null) {
            swipeRefreshLayout.setRefreshing(true);
            courseId = ((TextView) v.findViewById(R.id.hidden)).getText().toString();
            fileNameToDownload = null;
            reloadList();
        } else {
            fileNameToDownload = ((TextView) v.findViewById(R.id.name)).getText().toString();
            AumsResourcesActivityPermissionsDispatcher.downloadFileWithCheck(this);
        }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void downloadFile() {
        progressDialog.show();
        File destinationFolder = new File(Environment.getExternalStorageDirectory() + "/amrita-info-desk/docs/" + courseId + "/");
        if(!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }
        final File destinationFile = new File(destinationFolder + "/" + fileNameToDownload);
        if(destinationFile.exists()) {
            openFile(destinationFile, fileNameToDownload);
        } else {
            aums.downloadResource(courseId, fileNameToDownload, new FileResponse() {
                @SuppressWarnings("ResultOfMethodCallIgnored")
                @Override
                public void onSuccess(File file) {
                    if (fileNameToDownload == null) {
                        file.delete();
                    } else {
                        progressDialog.dismiss();
                        try {
                            FileUtils.copyFile(file, destinationFile);
                            openFile(destinationFile, fileNameToDownload);
                        } catch (IOException e) {
                            Snackbar.make(parentView, "An error occurred while saving the file", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    progressDialog.dismiss();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (courseId != null) {
            courseId = null;
            fileNameToDownload = null;
            CoursesListAdapter adapter = new CoursesListAdapter(courseDatas);
            recyclerView.setAdapter(adapter);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (courseId != null) {
            if (item.getItemId() == android.R.id.home) {
                courseId = null;
                fileNameToDownload = null;
                CoursesListAdapter adapter = new CoursesListAdapter(courseDatas);
                recyclerView.setAdapter(adapter);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showDeniedForExternalStorage() {
        Toast.makeText(this, "Storage permission is required for downloading resources", Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showNeverAskForExternalStorage() {
        Toast.makeText(this, "Storage permission is required for downloading resources", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AumsResourcesActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        public TextView name;
        public ImageView icon;
        TextView detail;
        TextView hidden;

        ViewHolder(View v) {
            super(v);
            name = ((TextView) v.findViewById(R.id.name));
            detail = ((TextView) v.findViewById(R.id.detail));
            hidden = ((TextView) v.findViewById(R.id.hidden));
            icon = ((ImageView) v.findViewById(R.id.icon));
        }
    }

    public class CoursesListAdapter extends RecyclerView.Adapter<ViewHolder> {

        private List<CourseData> courseDataList;

        CoursesListAdapter(List<CourseData> courseDataList) {
            this.courseDataList = courseDataList;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_aums_resources, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CourseData courseData = courseDataList.get(position);
            holder.name.setText(courseData.getCourseName());
            holder.detail.setText(courseData.getCourseCode());
            holder.hidden.setText(courseData.getId());
            holder.icon.setImageResource(R.drawable.ic_aums_folder);
        }

        @Override
        public int getItemCount() {
            return courseDataList.size();
        }
    }

    @SuppressWarnings({"unused", "MismatchedReadAndWriteOfArray"})
    public class CourseResourcesAdapter extends RecyclerView.Adapter<ViewHolder> {

        private List<CourseResource> courseResourceList;

        CourseResourcesAdapter(List<CourseResource> courseResourceList) {
            this.courseResourceList = courseResourceList;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_aums_resources, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CourseResource courseResource = courseResourceList.get(position);
            holder.detail.setVisibility(View.GONE);
            holder.name.setText(courseResource.getResourceFileName());

            String filename = courseResource.getResourceFileName().toLowerCase();

            String[] web = {"html", "htm", "mhtml"};
            String[] computer = {"exe", "dmg", "iso", "msi"};
            String[] document = {"doc", "docx", "ppt", "pps", "rtf", "txt", "pptx", "xls", "xlsx", "pdf", "odt"};
            String[] font = {"ttf", "otf"};
            String[] image = {"png", "gif", "jpg", "jpeg", "bmp"};
            String[] video = {"mp4", "mp3", "avi", "mov", "mpg", "mkv", "wmv"};

            if (FilenameUtils.isExtension(filename, web)) {
                holder.icon.setImageResource(R.drawable.ic_aums_web);
            } else if (FilenameUtils.isExtension(filename, computer)) {
                holder.icon.setImageResource(R.drawable.ic_aums_computer);
            } else if (FilenameUtils.isExtension(filename, document)) {
                holder.icon.setImageResource(R.drawable.ic_aums_document);
            } else if (FilenameUtils.isExtension(filename, font)) {
                holder.icon.setImageResource(R.drawable.ic_aums_font);
            } else if (FilenameUtils.isExtension(filename, image)) {
                holder.icon.setImageResource(R.drawable.ic_aums_image);
            } else if (FilenameUtils.isExtension(filename, video)) {
                holder.icon.setImageResource(R.drawable.ic_aums_video);
            } else {
                holder.icon.setImageResource(R.drawable.ic_aums_file);
            }
        }

        @Override
        public int getItemCount() {
            return courseResourceList.size();
        }
    }

    private void openFile(File file, String fileName) {
        Intent fileOpenIntent = new Intent(Intent.ACTION_VIEW);
        fileOpenIntent.setData(Uri.fromFile(file));
        Intent fileChooserIntent = Intent.createChooser(fileOpenIntent, "Open " + fileName + " with:");
        startActivity(fileChooserIntent);

    }
}
