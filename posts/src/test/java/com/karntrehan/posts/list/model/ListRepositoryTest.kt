package com.karntrehan.posts.list.model

import com.karntrehan.posts.commons.DummyData
import com.karntrehan.posts.commons.TestScheduler
import com.karntrehan.posts.commons.data.PostWithUser
import com.mpaani.core.networking.Outcome
import com.nhaarman.mockito_kotlin.*
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Tests for [ListRepository]
 * */
@RunWith(RobolectricTestRunner::class)
class ListRepositoryTest {
    private val local: ListDataContract.Local = mock()
    private val remote: ListDataContract.Remote = mock()

    private lateinit var repository: ListRepository
    private var compositeDisposable = CompositeDisposable()

    @Before
    fun init() {
        repository = ListRepository(local, remote, TestScheduler())
        whenever(local.getPostsWithUsers()).doReturn(Flowable.just(emptyList()))
        whenever(remote.getUsers()).doReturn(Flowable.just(emptyList()))
        whenever(remote.getPosts()).doReturn(Flowable.just(emptyList()))
    }

    /**
     * Verify if calling [ListRepository.fetchPosts] triggers [ListDataContract.Local.getPostsWithUsers]
     *  and it's result is added to the [ListRepository.postFetchOutcome]
     * */
    @Test
    fun testFetchPosts() {
        val postWithUsersSuccess = listOf(DummyData.PostWithUser(1), DummyData.PostWithUser(2))
        whenever(local.getPostsWithUsers()).doReturn(Flowable.just(postWithUsersSuccess))

        val obs = TestObserver<Outcome<List<PostWithUser>>>()

        repository.postFetchOutcome.subscribe(obs)
        obs.assertEmpty()

        repository.fetchPosts(compositeDisposable)
        verify(local).getPostsWithUsers()

        obs.assertValueAt(0, Outcome.loading(true))
        obs.assertValueAt(1, Outcome.loading(false))
        obs.assertValueAt(2, Outcome.success(postWithUsersSuccess))
    }

    /**
     * Verify if calling [ListRepository.fetchPosts] triggers [ListDataContract.Remote.getUsers]
     * & [ListDataContract.Remote.getPosts] if [ListRepository.remoteFetch] = true
     * */
    @Test
    fun testFirstFetchPostsTriggersRemote() {
        repository.remoteFetch = true
        repository.fetchPosts(compositeDisposable)
        verify(remote).getPosts()
        verify(remote).getUsers()
    }


    /**
     * Verify if calling [ListRepository.fetchPosts] NEVER triggers [ListDataContract.Remote.getUsers]
     * & [ListDataContract.Remote.getPosts] if [ListRepository.remoteFetch] = false
     * */
    @Test
    fun testSubsequentFetchPostsNeverTriggersRemote() {
        repository.remoteFetch = false
        repository.fetchPosts(compositeDisposable)
        verify(remote, never()).getPosts()
        verify(remote, never()).getUsers()
    }


    /**
     * Verify successful refresh of posts and users triggers [ListDataContract.Local.saveUsersAndPosts]
     * */
    @Test
    fun testRefreshPostsTriggersSave() {
        val dummyUsers = listOf(DummyData.User(1))
        val dummyPosts = listOf(DummyData.Post(1))
        whenever(remote.getUsers()).doReturn(Flowable.just(dummyUsers))
        whenever(remote.getPosts()).doReturn(Flowable.just(dummyPosts))

        repository.refreshPosts(compositeDisposable)
        verify(local).saveUsersAndPosts(dummyUsers, dummyPosts)
    }

    /**
     * Verify erred refresh of posts and users pushes to [ListDataContract.Repository.postFetchOutcome]
     * with error
     * */
    @Test
    fun testRefreshPostsFailurePushesToOutcome() {
        val exception = IOException()
        whenever(remote.getUsers()).doReturn(Flowable.error(exception))

        val obs = TestObserver<Outcome<List<PostWithUser>>>()
        repository.postFetchOutcome.subscribe(obs)

        repository.refreshPosts(compositeDisposable)

        obs.assertValueAt(0, Outcome.loading(true))
        obs.assertValueAt(1, Outcome.loading(false))
        obs.assertValueAt(2, Outcome.failure(exception))
    }
}