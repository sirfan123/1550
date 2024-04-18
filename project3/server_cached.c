#include <sys/stat.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <pthread.h>

#define CACHE_SIZE 5
#define STATS_FILE_THREAD "stats_thread_cached.txt"

pthread_mutex_t mutex;

// Structure to hold cached webpage data
typedef struct {
    char filename[1024];
    char content[1024]; 
    int size;
    int ref_count; // Reference count for multi-threaded access
    int valid; // Flag to indicate if the cached page is valid
} CachedPage;

CachedPage cache[CACHE_SIZE];
int cache_count = 0;

// Function to log the request statistics
void log_request_thread(char *filename, int size, double elapsed_time) { // All we rlly need to log is the file size and time 
    FILE *stats_file = fopen(STATS_FILE_THREAD, "a");
    if (stats_file != NULL) {
        fprintf(stats_file, "%s\t%d\t%.4f\n", filename, size, elapsed_time);
        fclose(stats_file);
    } else {
        perror("Error opening stats_thread_cached.txt file");
    }
}

// Function to increment the reference count of a cached page
void increment_ref_count(int index) {
    cache[index].ref_count++;
}

// Function to decrement the reference count of a cached page
void decrement_ref_count(int index) {
    pthread_mutex_lock(&mutex);
    cache[index].ref_count--;

    // If reference count becomes 0 and the page is not valid, reset the cache entry
    if (cache[index].ref_count == 0 && !cache[index].valid) {
        // Reset the cache entry
        memset(&cache[index], 0, sizeof(CachedPage));
        cache_count--;
    }
    pthread_mutex_unlock(&mutex);
}


// Function to find a cached page by filename
int find_cached_page(char *filename) {
    for (int i = 0; i < cache_count; i++) {
        if (strcmp(cache[i].filename, filename) == 0) {
            return i;
        }
    }
    return -1;
}

// Function to handle a client request in a thread
void *handle_request_thread(void *arg) {
    struct stat file_stats;
    struct timespec start_time, end_time;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &start_time); //START THE ELAPSED TIME
    int connfd = *((int *)arg);
    char buffer[1024];
    char filename[1024];
    int cache_index = -1; //Using this to signal if file is in our cache or not and what index it is

    memset(buffer, 0, sizeof(buffer));
    memset(filename, 0, sizeof(filename));

      // In HTTP, the client speaks first. So we recv their message
    // into our buffer.
    int amt = recv(connfd, buffer, sizeof(buffer), 0);
    fprintf(stderr, "%s", buffer);


     // We only can handle HTTP GET requests for files served
    // from the current working directory, which becomes the website root
    if (sscanf(buffer, "GET /%s", filename) < 1) {
        fprintf(stderr, "Bad HTTP request\n");
        close(connfd);
        pthread_exit(NULL);
    }
    

    // If the HTTP request is bigger than our buffer can hold, we need to call
    // recv() until we have no more data to read, otherwise it will be
    // there waiting for us on the next call to recv(). So we'll just
    // read it and discard it. GET should be the first 3 bytes, and we'll
    // assume paths that are smaller than about 1000 characters.
    if (amt == sizeof(buffer)) {
        // If recv returns as much as we asked for, there may be more data
        while (recv(connfd, buffer, sizeof(buffer), 0) == sizeof(buffer))
            /* discard */;
    }

    FILE *f = fopen(filename, "rb");

    if (f == NULL) {
            // Assume that failure to open the file means it doesn't exist
            char response[] = "HTTP/1.1 404 Not Found\n\n";
            send(connfd, response, strlen(response), 0);
        } else {
            // Read file content into buffer
            int size;
            char response[1024];

            strcpy(response, "HTTP/1.1 200 OK\n");
            send(connfd, response, strlen(response), 0);

            time_t now;
            time(&now);
            sprintf(response, "Date: %s", asctime(gmtime(&now)));
            send(connfd, response, strlen(response), 0);

            fstat(fileno(f), &file_stats);
            sprintf(response, "Content-Length: %d\n", file_stats.st_size);
            send(connfd, response, strlen(response), 0);

            strcpy(response, "Connection: close\n");
            send(connfd, response, strlen(response), 0);

            strcpy(response, "Content-Type: text/html\n\n");
            send(connfd, response, strlen(response), 0);

            fprintf(stderr, "File: %s\n", filename);

    // Try to find the requested page in cache
    cache_index = find_cached_page(filename);
   
    // If page found in cache
    if (cache_index != -1) {
         // Lock Mutex
        pthread_mutex_lock(&mutex);
        // Increment reference count since the page is being accessed by another thread
        increment_ref_count(cache_index);

        // Make a local copy of the pointer to the cached content
        char local_content[1024];
        strcpy(local_content, cache[cache_index].content);

        // Unlock Mutex
        pthread_mutex_unlock(&mutex);

        // Construct HTTP headers
        char headers[1024];
        sprintf(headers, "HTTP/1.1 200 OK\n");

        time_t now;
        time(&now);
        sprintf(headers + strlen(headers), "Date: %s", asctime(gmtime(&now)));
        sprintf(headers + strlen(headers), "Content-Length: %d\n", cache[cache_index].size);
        sprintf(headers + strlen(headers), "Connection: close\n");
        sprintf(headers + strlen(headers), "Content-Type: text/html\n\n");

        // Send HTTP headers and cached page content to client
        send(connfd, headers, strlen(headers), 0);
        send(connfd, local_content, cache[cache_index].size, 0);

        // Decrement reference count after usage
        decrement_ref_count(cache_index);
    } else {
            //Get file from disk its not in cache
            int bytes_read = 0;
            do {
                //read response amount of data at a time.
                //Note that sizeof() in C can only tell us the number of
                //elements in an array that is declared in scope. If you
                //move the declaration elsewhere, it will degrade into
                //the sizeof a pointer instead.
                bytes_read = fread(response, 1, sizeof(response), f);

                //if we read anything, send it
                if (bytes_read > 0) {
                    int sent = send(connfd, response, bytes_read, 0);
                    //It's possible that send wasn't able to send all of
                    //our response in one call. It will return how much it
                    //actually sent. Keep calling send until all of it is
                    //sent.
                    while (sent < bytes_read) {
                        sent += send(connfd, response + sent, bytes_read - sent, 0);
                    }
                }
        } while (bytes_read > 0 && bytes_read == sizeof(response));

            fclose(f);

            // Lock Mutex
            pthread_mutex_lock(&mutex);

            // Cache eviction if cache is full
            if (cache_count == CACHE_SIZE) {
            // Shift all pages left 1 index
            for (int i = 0; i < CACHE_SIZE - 1; i++) {
                strcpy(cache[i].filename, cache[i + 1].filename);
                strcpy(cache[i].content, cache[i + 1].content);
                cache[i].size = cache[i + 1].size;
                cache[i].ref_count = cache[i + 1].ref_count;
                cache[i].valid = cache[i + 1].valid;
            }

            // Overwrite the last element with the new page (Effectively we have removed the oldest page)
            strcpy(cache[CACHE_SIZE - 1].filename, filename);
            strcpy(cache[CACHE_SIZE - 1].content, response);
            cache[CACHE_SIZE - 1].size = file_stats.st_size;
            cache[CACHE_SIZE - 1].ref_count = 1; // Initialize reference count
            cache[CACHE_SIZE - 1].valid = 1; // Set page as valid
                } else {
            // Add the new page to cache
            strcpy(cache[cache_count].filename, filename);
            strcpy(cache[cache_count].content, response);
            cache[cache_count].size = file_stats.st_size;
            cache[cache_count].ref_count = 1; // Initialize reference count
            cache[cache_count].valid = 1; // Set page as valid
            cache_count++;
            }

            // Unlock Mutex
            pthread_mutex_unlock(&mutex);

            }
        }

    // Calculate and log the elapsed time
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &end_time);
    double elapsed_time = (end_time.tv_sec - start_time.tv_sec) + (end_time.tv_nsec - start_time.tv_nsec) / 1e9;

    // Lock Mutex for logging
    pthread_mutex_lock(&mutex);

    // Log request statistics
    if (cache_index == -1) {
        log_request_thread(filename, file_stats.st_size, elapsed_time);
    } else {
        log_request_thread(filename, cache[cache_index].size, elapsed_time);
    }

    // Unlock Mutex after logging
    pthread_mutex_unlock(&mutex);

    // Close the connection
    shutdown(connfd, SHUT_RDWR);
    close(connfd);

    pthread_exit(NULL);
}

int main() {
    // Initialize mutex
    if (pthread_mutex_init(&mutex, NULL) != 0) {
        perror("Mutex initialization failed");
        exit(EXIT_FAILURE);
    }

    // Sockets represent potential connections
    // We make an internet socket
    int sfd = socket(PF_INET, SOCK_STREAM, 0);
    if (-1 == sfd) {
        perror("Cannot create socket\n");
        exit(EXIT_FAILURE);
    }

    //We will configure it to use this machine's IP, or
    //for us, localhost (127.0.0.1)
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));

    addr.sin_family = AF_INET;
    //Web servers always listen on port 80
    addr.sin_port = htons(80);
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    //So we bind our socket to port 80
    if (-1 == bind(sfd, (struct sockaddr *)&addr, sizeof(addr))) {
        perror("Bind failed");
        exit(EXIT_FAILURE);
    }

    //And set it up as a listening socket with a backlog of 10 pending connections
    if (-1 == listen(sfd, 10)) {
        perror("Listen failed");
        exit(EXIT_FAILURE);
    }

    //A server's gotta serve...
    for (;;) {
        int connfd = accept(sfd, NULL, NULL);
        if (connfd < 0) {
            perror("Accept failed");
            exit(EXIT_FAILURE);
        }

        pthread_t tid;
        if (pthread_create(&tid, NULL, handle_request_thread, &connfd) != 0) {
            perror("Thread creation failed");
            exit(EXIT_FAILURE);
        }

        pthread_detach(tid); // Detach the thread to avoid memory leaks
    }

    // Close the server socket and destroy the mutex
    close(sfd);
    pthread_mutex_destroy(&mutex);

    return 0;
}