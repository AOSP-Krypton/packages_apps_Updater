/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.model.data;

public class Response {
    private Object body;
    private int status;

    public Response(int status) {
        this.status = status;
    }

    public Response(Object body) {
        this.body = body;
    }

    public Response(Object body, int status) {
        this.body = body;
        this.status = status;
    }

    public Object getResponseBody() {
        return body;
    }

    public int getStatus() {
        return status;
    }
}
