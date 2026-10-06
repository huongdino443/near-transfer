using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace NearTransfer.Windows;

public static class SmoothScrollBehavior
{
    public static readonly DependencyProperty IsEnabledProperty =
        DependencyProperty.RegisterAttached(
            "IsEnabled",
            typeof(bool),
            typeof(SmoothScrollBehavior),
            new PropertyMetadata(false, OnIsEnabledChanged));

    private static readonly DependencyProperty ScrollStateProperty =
        DependencyProperty.RegisterAttached(
            "ScrollState",
            typeof(ScrollState),
            typeof(SmoothScrollBehavior),
            new PropertyMetadata(null));

    public static bool GetIsEnabled(DependencyObject element) =>
        (bool)element.GetValue(IsEnabledProperty);

    public static void SetIsEnabled(DependencyObject element, bool value) =>
        element.SetValue(IsEnabledProperty, value);

    private static void OnIsEnabledChanged(
        DependencyObject element,
        DependencyPropertyChangedEventArgs args)
    {
        if (element is not FrameworkElement frameworkElement)
        {
            return;
        }

        if ((bool)args.NewValue)
        {
            frameworkElement.Loaded += Element_Loaded;
            if (frameworkElement.IsLoaded)
            {
                Attach(frameworkElement);
            }
        }
        else
        {
            frameworkElement.Loaded -= Element_Loaded;
            frameworkElement.Unloaded -= Element_Unloaded;
            Detach(frameworkElement);
        }
    }

    private static void Element_Loaded(object sender, RoutedEventArgs args)
    {
        if (sender is FrameworkElement element)
        {
            element.Loaded -= Element_Loaded;
            element.Unloaded += Element_Unloaded;
            Attach(element);
        }
    }

    private static void Element_Unloaded(object sender, RoutedEventArgs args)
    {
        if (sender is FrameworkElement element)
        {
            Detach(element);
        }
    }

    private static void Attach(FrameworkElement element)
    {
        element.ApplyTemplate();
        var viewer = element as ScrollViewer ?? FindScrollViewer(element);
        if (viewer is null || viewer.GetValue(ScrollStateProperty) is ScrollState)
        {
            return;
        }

        viewer.SetValue(ScrollStateProperty, new ScrollState(viewer));
        viewer.PreviewMouseWheel += Viewer_PreviewMouseWheel;
    }

    private static void Detach(FrameworkElement element)
    {
        var viewer = element as ScrollViewer ?? FindScrollViewer(element);
        if (viewer is null || viewer.GetValue(ScrollStateProperty) is not ScrollState state)
        {
            return;
        }

        viewer.PreviewMouseWheel -= Viewer_PreviewMouseWheel;
        state.Stop();
        viewer.ClearValue(ScrollStateProperty);
    }

    private static void Viewer_PreviewMouseWheel(object sender, MouseWheelEventArgs args)
    {
        if (sender is not ScrollViewer viewer ||
            viewer.GetValue(ScrollStateProperty) is not ScrollState state ||
            viewer.ScrollableHeight <= 0)
        {
            return;
        }

        var lines = SystemParameters.WheelScrollLines;
        var distance = lines < 0
            ? Math.Max(1, viewer.ViewportHeight) * 0.85
            : Math.Max(1, lines) * 34d;
        distance *= args.Delta / 120d;

        var current = state.IsAnimating ? state.TargetOffset : viewer.VerticalOffset;
        var target = LegacyMath.Clamp(current - distance, 0, viewer.ScrollableHeight);
        if (Math.Abs(target - current) < 0.5)
        {
            return;
        }

        args.Handled = true;
        state.ScrollTo(target);
    }

    private static ScrollViewer? FindScrollViewer(DependencyObject root)
    {
        var childCount = VisualTreeHelper.GetChildrenCount(root);
        for (var index = 0; index < childCount; index++)
        {
            var child = VisualTreeHelper.GetChild(root, index);
            if (child is ScrollViewer viewer)
            {
                return viewer;
            }

            var descendant = FindScrollViewer(child);
            if (descendant is not null)
            {
                return descendant;
            }
        }

        return null;
    }

    private sealed class ScrollState
    {
        private readonly ScrollViewer _viewer;
        private readonly DispatcherTimer _timer = new()
        {
            Interval = TimeSpan.FromMilliseconds(16)
        };
        private readonly Stopwatch _stopwatch = new();
        private double _startOffset;
        private double _targetOffset;

        public ScrollState(ScrollViewer viewer)
        {
            _viewer = viewer;
            _timer.Tick += Timer_Tick;
        }

        public bool IsAnimating => _timer.IsEnabled;
        public double TargetOffset => _targetOffset;

        public void ScrollTo(double offset)
        {
            _startOffset = _viewer.VerticalOffset;
            _targetOffset = offset;
            _stopwatch.Restart();
            if (!_timer.IsEnabled)
            {
                _timer.Start();
            }
        }

        public void Stop()
        {
            _timer.Stop();
            _stopwatch.Stop();
        }

        private void Timer_Tick(object? sender, EventArgs args)
        {
            const double durationMilliseconds = 180;
            var progress = LegacyMath.Clamp(
                _stopwatch.Elapsed.TotalMilliseconds / durationMilliseconds,
                0,
                1);
            var eased = 1 - Math.Pow(1 - progress, 3);
            var offset = _startOffset + (_targetOffset - _startOffset) * eased;
            _viewer.ScrollToVerticalOffset(offset);

            if (progress >= 1)
            {
                _viewer.ScrollToVerticalOffset(_targetOffset);
                _timer.Stop();
                _stopwatch.Stop();
            }
        }
    }
}